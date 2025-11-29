import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientHandler implements Runnable {

    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;

    private int estadoLogin = ESTADO_INICIAL;
    private int userId;
    private String userEmail;

    private final Socket clientSocket;
    private final DatabaseManager dbManager;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final ServerAPI serverAPI;

    private static final int AUTH_TIMEOUT_MS = 30000;

    public ClientHandler(Socket socket, DatabaseManager dbManager, ServerAPI api) throws IOException {
        this.clientSocket = socket;
        this.dbManager = dbManager;

        System.out.println("[Handler] A inicializar streams para " + socket.getInetAddress());
        this.out = new ObjectOutputStream(clientSocket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(clientSocket.getInputStream());
        this.serverAPI = api;
    }

    @Override
    public void run() {
        try {
            clientSocket.setSoTimeout(AUTH_TIMEOUT_MS);

            // --- FASE 1: AUTENTICAÇÃO ---
            while (estadoLogin == ESTADO_INICIAL) {
                try {
                    Object msg = in.readObject();
                    if (msg instanceof MsgRegisto) processarRegisto((MsgRegisto) msg);
                    else if (msg instanceof MsgLogin) processarLogin((MsgLogin) msg);
                    else enviarObjeto("ERRO: Login/Registo necessário.");
                } catch (SocketTimeoutException e) {
                    enviarObjeto("TIMEOUT: Login expirou.");
                    return;
                }
            }

            clientSocket.setSoTimeout(0);
            System.out.println("[Handler] Cliente " + userEmail + " autenticado.");

            // --- FASE 2: INTERAÇÃO ---
            while (true) {
                Object msg = in.readObject();

                if (estadoLogin == ESTADO_DOCENTE) {
                    if (msg instanceof MsgCriarPergunta) {
                        processarCriarPergunta((MsgCriarPergunta) msg);
                    }
                    else if (msg instanceof MsgObterPerguntas) {
                        List<Pergunta> lista = dbManager.obterPerguntasDoDocente(userId);
                        enviarObjeto(lista);
                    }
                    // --- CORREÇÃO: ADICIONAR ESTE BLOCO AQUI ---
                    else if (msg instanceof MsgObterPergunta) {
                        // O Docente também precisa de obter uma pergunta individual para o CSV
                        processarObterPergunta((MsgObterPergunta) msg);
                    }
                    // -------------------------------------------
                    // Dentro do bloco else if (estadoLogin == ESTADO_DOCENTE)
                    else if (msg instanceof MsgObterRespostas) {
                        String codigo = ((MsgObterRespostas) msg).getCodigoAcesso();

                        // NOVO: Obter o ID do docente dono da pergunta
                        int donoID = dbManager.obterDocenteIDDaPergunta(codigo); // Mudar esta chamada no DBManager

                        if (donoID == userId) {
                            // Apenas se for o dono
                            List<RespostaEstudante> resps = dbManager.obterRespostasDaPergunta(codigo);
                            enviarObjeto(resps);
                        } else {
                            // Se não for o dono, ou a pergunta não existir (donoID = -1)
                            enviarObjeto("ERRO: Pergunta não encontrada ou não pertence a este Docente.");
                        }
                    }
                }
                else if (estadoLogin == ESTADO_ESTUDANTE) {
                    if (msg instanceof MsgObterPergunta) {
                        processarObterPergunta((MsgObterPergunta) msg);
                    } else if (msg instanceof MsgResponderPergunta) {
                        processarResponderPergunta((MsgResponderPergunta) msg);
                    }
                }
            }

        } catch (EOFException | SocketException e) {
            System.out.println("[Handler] Cliente saiu: " + userEmail);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }

    private synchronized void enviarObjeto(Object obj) throws IOException {
        out.writeObject(obj);
        out.flush();
    }

    public String getUserEmail() {
        return userEmail; // Assumindo que userEmail é o campo de estado
    }

    public boolean enviarNotificacao(String mensagem) {
        try {
            enviarObjeto("NOTIFICACAO:" + mensagem);
            return true;
        } catch (IOException e) {
            return false; // Falha ao enviar, indica que a conexão caiu
        }
    }

    // --- PROCESSAMENTO ---

    private void processarRegisto(MsgRegisto msg) throws IOException {
        boolean sucesso = false;
        if (msg.isDocente()) {
            if (!dbManager.validarCodigoDocente(msg.getCodigoDocente())) {
                enviarObjeto("ERRO: Código de docente inválido.");
                return;
            }
            sucesso = Boolean.parseBoolean(dbManager.registarDocente(msg.getDocente()));
        } else if (msg.isEstudante()) {
            sucesso = Boolean.parseBoolean(dbManager.registarEstudante(msg.getEstudante()));
        }
        if (sucesso) enviarObjeto("SUCESSO: Registado!");
        else enviarObjeto("ERRO: Dados inválidos ou duplicados.");
    }

    private void processarLogin(MsgLogin msg) throws IOException {
        if (dbManager.autenticarDocente(msg.getEmail(), msg.getPassword())) {
            estadoLogin = ESTADO_DOCENTE;
            userEmail = msg.getEmail();
            userId = dbManager.obterIdDocente(msg.getEmail());
            enviarObjeto("SUCESSO: Login Docente");
        } else if (dbManager.autenticarEstudante(msg.getEmail(), msg.getPassword())) {
            estadoLogin = ESTADO_ESTUDANTE;
            userEmail = msg.getEmail();
            userId = dbManager.obterIdEstudante(msg.getEmail());
            enviarObjeto("SUCESSO: Login Estudante");
        } else {
            enviarObjeto("ERRO: Credenciais inválidas.");
        }
    }

    private void processarCriarPergunta(MsgCriarPergunta msg) throws IOException {
        String codAcesso = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Assumindo que dbManager.criarPergunta agora retorna a Query SQL executada
        String querySql = dbManager.criarPergunta(userId, msg.getEnunciado(), codAcesso, msg.getInicio(), msg.getFim(), msg.getOpcoes());

        if (querySql != null) {
            // Obter a versão ATUALIZADA da BD (deve ser a nova versão)
            int novaVersao = dbManager.getVersaoBD();

            // 1. DISPARAR HEARTBEAT DE ESCRITA para Backups
            serverAPI.publicarAlteracao(querySql, novaVersao);

            // 2. DISPARAR NOTIFICAÇÃO para Outros Clientes
            serverAPI.notificarTodosClientes("Nova pergunta criada: " + codAcesso);

            enviarObjeto("SUCESSO: Criada. Código: " + codAcesso);
        }
        // ...
    }

    private void processarObterPergunta(MsgObterPergunta msg) throws IOException {
        Pergunta p = dbManager.obterPerguntaPorCodigo(msg.getCodigoAcesso());
        enviarObjeto(p);
    }

    private void processarResponderPergunta(MsgResponderPergunta msg) throws IOException {
        boolean sucesso = Boolean.parseBoolean(dbManager.registarResposta(userId, msg.getCodigoAcesso(), msg.getLetraOpcao()));
        if (sucesso) enviarObjeto("SUCESSO: Resposta guardada.");
        else enviarObjeto("ERRO: Falha (já respondeste?).");
    }
}