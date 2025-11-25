import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.UUID;

public class ClientHandler implements Runnable {

    // Constantes de Estado do Cliente
    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;

    private int estadoLogin = ESTADO_INICIAL;
    private int userId; // ID do utilizador na BD (para saber quem está a fazer as ações)
    private String userEmail;

    private final Socket clientSocket;
    private final DatabaseManager dbManager;

    // Timeout para login (30 segundos) para não ocupar recursos com clientes "mortos"
    private static final int AUTH_TIMEOUT_MS = 30000;

    public ClientHandler(Socket socket, DatabaseManager dbManager) {
        this.clientSocket = socket;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        System.out.println("[Handler] Novo Cliente conectado: " + clientSocket.getInetAddress());

        try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

            // Define timeout apenas para a fase de login
            clientSocket.setSoTimeout(AUTH_TIMEOUT_MS);

            // --- FASE 1: AUTENTICAÇÃO ---
            while (estadoLogin == ESTADO_INICIAL) {
                try {
                    Object msg = in.readObject();

                    if (msg instanceof MsgRegisto) {
                        processarRegisto((MsgRegisto) msg, out);
                    } else if (msg instanceof MsgLogin) {
                        processarLogin((MsgLogin) msg, out);
                    } else {
                        out.writeObject("ERRO: Deve fazer login ou registo primeiro.");
                        out.flush();
                    }
                } catch (SocketTimeoutException e) {
                    out.writeObject("TIMEOUT: Demorou muito tempo a fazer login.");
                    return; // Encerra a thread
                }
            }

            // Remove timeout para a fase de interação normal
            clientSocket.setSoTimeout(0);
            System.out.println("[Handler] Cliente " + userEmail + " autenticado. A iniciar interação.");

            // --- FASE 2: INTERAÇÃO (LOOP PRINCIPAL) ---
            while (true) {
                Object msg = in.readObject();

                // Se for Docente
                if (estadoLogin == ESTADO_DOCENTE) {
                    if (msg instanceof MsgCriarPergunta) {
                        processarCriarPergunta((MsgCriarPergunta) msg, out);
                    }
                }

                // Se for Estudante
                else if (estadoLogin == ESTADO_ESTUDANTE) {
                    if (msg instanceof MsgObterPergunta) {
                        processarObterPergunta((MsgObterPergunta) msg, out);
                    } else if (msg instanceof MsgResponderPergunta) {
                        processarResponderPergunta((MsgResponderPergunta) msg, out);
                    }
                }
            }

        } catch (EOFException | SocketException e) {
            System.out.println("[Handler] Cliente desconectou-se: " + userEmail);
        } catch (Exception e) {
            System.err.println("[Handler] Erro inesperado: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }

    // ---------------------------------------------------------
    // MÉTODOS DE AUTENTICAÇÃO
    // ---------------------------------------------------------

    private void processarLogin(MsgLogin msg, ObjectOutputStream out) throws IOException {
        String email = msg.getEmail();
        String password = msg.getPassword();
        String resposta;

        // Tenta Docente
        if (dbManager.autenticarDocente(email, password)) {
            estadoLogin = ESTADO_DOCENTE;
            userEmail = email;
            userId = dbManager.obterIdDocente(email); // Guarda o ID para usar depois
            resposta = "SUCESSO: Login efetuado com sucesso Docente";
        }
        // Tenta Estudante
        else if (dbManager.autenticarEstudante(email, password)) {
            estadoLogin = ESTADO_ESTUDANTE;
            userEmail = email;
            userId = dbManager.obterIdEstudante(email); // Guarda o ID para usar depois
            resposta = "SUCESSO: Login efetuado com sucesso Estudante";
        }
        // Falhou
        else {
            resposta = "ERRO: Credenciais inválidas.";
        }

        out.writeObject(resposta);
        out.flush();
    }

    private void processarRegisto(MsgRegisto msg, ObjectOutputStream out) throws IOException {
        boolean sucesso = false;

        if (msg.isDocente()) {
            sucesso = dbManager.registarDocente(msg.getDocente());
        } else if (msg.isEstudante()) {
            sucesso = dbManager.registarEstudante(msg.getEstudante());
        }

        if (sucesso) {
            out.writeObject("SUCESSO: Utilizador registado!");
        } else {
            out.writeObject("ERRO: Registo falhou (Email já existe?).");
        }
        out.flush();
    }

    // ---------------------------------------------------------
    // MÉTODOS DO DOCENTE
    // ---------------------------------------------------------

    private void processarCriarPergunta(MsgCriarPergunta msg, ObjectOutputStream out) throws IOException {
        // Gera um código aleatório simples (ex: A1B2C3)
        String codAcesso = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        boolean sucesso = dbManager.criarPergunta(
                userId, // Usa o ID do docente logado
                msg.getEnunciado(),
                codAcesso,
                msg.getInicio(),
                msg.getFim(),
                msg.getOpcoes()
        );

        if (sucesso) {
            out.writeObject("SUCESSO: Pergunta criada. Código de acesso: " + codAcesso);
        } else {
            out.writeObject("ERRO: Falha ao criar pergunta na BD.");
        }
        out.flush();
    }

    // ---------------------------------------------------------
    // MÉTODOS DO ESTUDANTE
    // ---------------------------------------------------------

    private void processarObterPergunta(MsgObterPergunta msg, ObjectOutputStream out) throws IOException {
        System.out.println("[Handler] Aluno pede pergunta: " + msg.getCodigoAcesso());

        // Vai à BD buscar a pergunta
        Pergunta p = dbManager.obterPerguntaPorCodigo(msg.getCodigoAcesso());

        // Envia o objeto Pergunta (ou null se não existir)
        out.writeObject(p);
        out.flush();
    }

    private void processarResponderPergunta(MsgResponderPergunta msg, ObjectOutputStream out) throws IOException {
        System.out.println("[Handler] Aluno responde à pergunta: " + msg.getCodigoAcesso());

        // Grava a resposta na BD usando o ID do aluno logado (userId)
        boolean sucesso = dbManager.registarResposta(userId, msg.getCodigoAcesso(), msg.getLetraOpcao());

        if (sucesso) {
            out.writeObject("SUCESSO: Resposta registada!");
        } else {
            out.writeObject("ERRO: Já respondeste ou código inválido.");
        }
        out.flush();
    }
}