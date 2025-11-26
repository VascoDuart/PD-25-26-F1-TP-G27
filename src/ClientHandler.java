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

    private static final int AUTH_TIMEOUT_MS = 30000;

    public ClientHandler(Socket socket, DatabaseManager dbManager) throws IOException {
        this.clientSocket = socket;
        this.dbManager = dbManager;

        System.out.println("[Handler] A inicializar streams para " + socket.getInetAddress());
        this.out = new ObjectOutputStream(clientSocket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(clientSocket.getInputStream());
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
                    // --- NOVAS FUNCIONALIDADES ---
                    else if (msg instanceof MsgObterPerguntas) {
                        List<Pergunta> lista = dbManager.obterPerguntasDoDocente(userId);
                        enviarObjeto(lista);
                    }
                    else if (msg instanceof MsgObterRespostas) {
                        String codigo = ((MsgObterRespostas) msg).getCodigoAcesso();
                        // Verifica se existe (podia verificar também se pertence ao docente)
                        Pergunta p = dbManager.obterPerguntaPorCodigo(codigo);
                        if (p != null) {
                            List<RespostaEstudante> resps = dbManager.obterRespostasDaPergunta(codigo);
                            enviarObjeto(resps);
                        } else {
                            enviarObjeto(new ArrayList<RespostaEstudante>()); // Lista vazia
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

    public void enviarNotificacao(String mensagem) {
        try {
            enviarObjeto("NOTIFICACAO:" + mensagem);
        } catch (IOException e) {}
    }

    // --- PROCESSAMENTO ---

    private void processarRegisto(MsgRegisto msg) throws IOException {
        boolean sucesso = false;
        if (msg.isDocente()) {
            if (!dbManager.validarCodigoDocente(msg.getCodigoDocente())) {
                enviarObjeto("ERRO: Código de docente inválido.");
                return;
            }
            sucesso = dbManager.registarDocente(msg.getDocente());
        } else if (msg.isEstudante()) {
            sucesso = dbManager.registarEstudante(msg.getEstudante());
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
        boolean sucesso = dbManager.criarPergunta(userId, msg.getEnunciado(), codAcesso, msg.getInicio(), msg.getFim(), msg.getOpcoes());
        if (sucesso) enviarObjeto("SUCESSO: Criada. Código: " + codAcesso);
        else enviarObjeto("ERRO: Falha na BD.");
    }

    private void processarObterPergunta(MsgObterPergunta msg) throws IOException {
        Pergunta p = dbManager.obterPerguntaPorCodigo(msg.getCodigoAcesso());
        enviarObjeto(p);
    }

    private void processarResponderPergunta(MsgResponderPergunta msg) throws IOException {
        boolean sucesso = dbManager.registarResposta(userId, msg.getCodigoAcesso(), msg.getLetraOpcao());
        if (sucesso) enviarObjeto("SUCESSO: Resposta guardada.");
        else enviarObjeto("ERRO: Falha (já respondeste?).");
    }
}