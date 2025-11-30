import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {

    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;

    private int estadoLogin = ESTADO_INICIAL;
    private int userId;
    private String userEmail;

    private final Socket clientSocket;
    private final DatabaseManager dbManager;
    private final ServerAPI serverAPI;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    private static final int AUTH_TIMEOUT_MS = 30000;

    public ClientHandler(Socket socket, DatabaseManager dbManager, ServerAPI api) throws IOException {
        this.clientSocket = socket;
        this.dbManager = dbManager;
        this.serverAPI = api;

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
                    return; // Sai do handler
                }
            }

            clientSocket.setSoTimeout(0); // Remove o timeout após o login
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
                    else if (msg instanceof MsgObterPergunta) {
                        processarObterPergunta((MsgObterPergunta) msg);
                    }
                    else if (msg instanceof MsgObterRespostas) {
                        processarObterRespostas((MsgObterRespostas) msg);
                    }
                    // --- NOVOS CASOS DE USO ---
                    else if (msg instanceof MsgEliminarPergunta) {
                        processarEliminarPergunta((MsgEliminarPergunta) msg);
                    }
                    else if (msg instanceof MsgEditarPergunta) {
                        processarEditarPergunta((MsgEditarPergunta) msg);
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

    // --- MÉTODOS AUXILIARES ---

    private synchronized void enviarObjeto(Object obj) throws IOException {
        out.writeObject(obj);
        out.flush();
    }

    public boolean enviarNotificacao(String mensagem) {
        try {
            enviarObjeto("NOTIFICACAO:" + mensagem);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public String getUserEmail() {
        return userEmail;
    }

    // --- PROCESSAMENTO DE MENSAGENS ---

    private void processarRegisto(MsgRegisto msg) throws IOException {
        String resposta = "ERRO: Falha no registo.";

        synchronized (serverAPI.getBDLock()) {
            if (msg.isDocente()) {
                if (!dbManager.validarCodigoDocente(msg.getCodigoDocente())) {
                    resposta = "ERRO: Código de docente inválido.";
                } else {
                    String querySql = dbManager.registarDocente(msg.getDocente());
                    if (querySql != null) {
                        resposta = "SUCESSO: Docente registado!";
                        serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                    } else {
                        resposta = "ERRO: Docente ja registado ou email duplicado.";
                    }
                }
            } else if (msg.isEstudante()) {
                String querySql = dbManager.registarEstudante(msg.getEstudante());
                if (querySql != null) {
                    resposta = "SUCESSO: Estudante registado!";
                    serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                } else {
                    resposta = "ERRO: Dados inválidos ou duplicados.";
                }
            }
        }
        enviarObjeto(resposta);
    }

    private void processarLogin(MsgLogin msg) throws IOException {
        if (dbManager.autenticarDocente(msg.getEmail(), msg.getPassword())) {
            estadoLogin = ESTADO_DOCENTE;
            userEmail = msg.getEmail();
            userId = dbManager.obterIdDocente(msg.getEmail());
            enviarObjeto("SUCESSO: Login Docente");
            clientSocket.setSoTimeout(0);
        } else if (dbManager.autenticarEstudante(msg.getEmail(), msg.getPassword())) {
            estadoLogin = ESTADO_ESTUDANTE;
            userEmail = msg.getEmail();
            userId = dbManager.obterIdEstudante(msg.getEmail());
            enviarObjeto("SUCESSO: Login Estudante");
            clientSocket.setSoTimeout(0);
        } else {
            enviarObjeto("ERRO: Credenciais inválidas.");
        }
    }

    private void processarCriarPergunta(MsgCriarPergunta msg) throws IOException {
        String resposta = "ERRO: Falha na BD.";

        synchronized (serverAPI.getBDLock()) {
            String codAcesso = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            String querySql = dbManager.criarPergunta(userId, msg.getEnunciado(), codAcesso, msg.getInicio(), msg.getFim(), msg.getOpcoes());

            if (querySql != null) {
                resposta = "SUCESSO: Criada. Código: " + codAcesso;
                serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                serverAPI.notificarTodosClientes("Nova pergunta disponivel: " + codAcesso);
            }
        }
        enviarObjeto(resposta);
    }

    private void processarObterPergunta(MsgObterPergunta msg) throws IOException {
        Pergunta p = dbManager.obterPerguntaPorCodigo(msg.getCodigoAcesso());

        if (estadoLogin == ESTADO_ESTUDANTE && p != null) {
            List<Opcao> opcoesFiltradas = p.getOpcoes().stream()
                    .map(o -> new Opcao(o.getLetra(), o.getTexto(), false))
                    .collect(Collectors.toList());
        }
        enviarObjeto(p);
    }

    private void processarResponderPergunta(MsgResponderPergunta msg) throws IOException {
        String resposta = "ERRO: Falha (já respondeste ou pergunta invalida?).";

        synchronized (serverAPI.getBDLock()) {
            String querySql = dbManager.registarResposta(userId, msg.getCodigoAcesso(), msg.getLetraOpcao());

            if (querySql != null) {
                resposta = "SUCESSO: Resposta guardada.";
                serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
            }
        }
        enviarObjeto(resposta);
    }

    private void processarObterRespostas(MsgObterRespostas msg) throws IOException {
        String codigo = msg.getCodigoAcesso();
        int donoID = dbManager.obterDocenteIDDaPergunta(codigo);

        if (donoID == userId) {
            Pergunta pCompleta = dbManager.obterPerguntaPorCodigo(codigo);

            if (pCompleta != null) {
                List<RespostaEstudante> resps = dbManager.obterRespostasDaPergunta(codigo);
                List<Object> resultado = new ArrayList<>();
                resultado.add(pCompleta);
                resultado.add(resps);
                enviarObjeto(resultado);
            } else {
                enviarObjeto(new ArrayList<Object>());
            }
        } else {
            enviarObjeto("ERRO: Pergunta não encontrada ou não pertence a este Docente.");
        }
    }

    // --- NOVOS MÉTODOS DE PROCESSAMENTO (EDITAR/ELIMINAR) ---

    private void processarEliminarPergunta(MsgEliminarPergunta msg) throws IOException {
        String resposta = "ERRO: Não foi possível eliminar (verifique se tem respostas ou permissões).";

        synchronized (serverAPI.getBDLock()) {
            if (dbManager.podeAlterarPergunta(msg.getCodigoAcesso(), userId)) {
                String querySql = dbManager.eliminarPergunta(msg.getCodigoAcesso());

                if (querySql != null) {
                    resposta = "SUCESSO: Pergunta eliminada.";
                    serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                    serverAPI.notificarTodosClientes("Pergunta removida: " + msg.getCodigoAcesso());
                }
            }
        }
        enviarObjeto(resposta);
    }

    private void processarEditarPergunta(MsgEditarPergunta msg) throws IOException {
        String resposta = "ERRO: Não foi possível editar (verifique se tem respostas ou permissões).";

        synchronized (serverAPI.getBDLock()) {
            if (dbManager.podeAlterarPergunta(msg.getCodigoAcesso(), userId)) {
                String querySql = dbManager.editarPergunta(
                        msg.getCodigoAcesso(),
                        msg.getNovoEnunciado(),
                        msg.getNovoInicio(),
                        msg.getNovoFim()
                );

                if (querySql != null) {
                    resposta = "SUCESSO: Pergunta editada.";
                    serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                }
            }
        }
        enviarObjeto(resposta);
    }
}