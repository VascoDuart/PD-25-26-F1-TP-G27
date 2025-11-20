import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class ClientHandler implements Runnable {

    // Variáveis de Estado do Cliente
    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;
    private int estadoLogin = ESTADO_INICIAL;

    private final Socket clientSocket;
    private final DatabaseManager dbManager; // O Servidor injeta o DatabaseManager

    // Timeout para login/registo (30 segundos), conforme requisito
    private static final int AUTH_TIMEOUT_MS = 30000;

    public ClientHandler(Socket socket, DatabaseManager dbManager) {
        this.clientSocket = socket;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        System.out.println("[Handler] Novo Cliente conectado: " + clientSocket.getInetAddress());

        ObjectOutputStream out = null;
        ObjectInputStream in = null;

        try {
            // Define timeout para autenticação/registo
            clientSocket.setSoTimeout(AUTH_TIMEOUT_MS);

            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            // Loop de autenticação/registo
            while (estadoLogin == ESTADO_INICIAL) {

                // 1. Receber o pedido do cliente
                Object msg = in.readObject();

                if (msg instanceof MsgRegisto) {
                    processarRegisto((MsgRegisto) msg, out);
                } else if (msg instanceof MsgLogin) {
                    processarLogin((MsgLogin) msg, out);
                } else {
                    out.writeObject("Erro: Mensagem inicial inválida.");
                    out.flush();
                }
            }

            // Cliente autenticado. Removemos o timeout.
            clientSocket.setSoTimeout(0);
            System.out.println("[Handler] Cliente autenticado (" + (estadoLogin == ESTADO_DOCENTE ? "Docente" : "Estudante") + "). A iniciar interação.");

            // 2. Loop principal de interação (após login)
            while (clientSocket.isConnected()) {
                // TODO: Receber e processar comandos do utilizador autenticado

                // Implementação provisória para manter o thread vivo.
                // Na versão final, deve processar comandos lidos de 'in'.
                Thread.sleep(100);
            }

        } catch (SocketTimeoutException e) {
            // O cliente não enviou credenciais a tempo
            System.err.println("[Handler] Cliente excedeu o tempo limite de autenticação/registo (30s). Fechando.");
        } catch (EOFException | SocketException e) {
            // O cliente fechou a ligação (logout, crash, etc.)
            System.out.println("[Handler] Conexão TCP fechada pelo cliente ou Servidor.");
        } catch (Exception e) {
            // Captura qualquer outra exceção (incluindo falhas de DB ou IO)
            System.err.println("[Handler] ERRO INESPERADO no thread do cliente: " + e.getMessage());
            e.printStackTrace(); // Imprime a stack trace para diagnóstico
        } finally {
            // CRÍTICO: Fecha o socket no final para libertar o porto
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    System.out.println("[Handler] Conexão com Cliente fechada.");
                }
            } catch (IOException e) {
                // Ignorar erro ao fechar
            }
        }
    }

    /**
     * Processa a mensagem de login, verifica credenciais e define o estado do cliente.
     */
    private void processarLogin(MsgLogin msg, ObjectOutputStream out) throws IOException {
        String email = msg.getEmail();
        String password = msg.getPassword();
        String resposta;

        // 1. Tentar autenticar como DOCENTE
        if (dbManager.autenticarDocente(email, password)) {
            estadoLogin = ESTADO_DOCENTE;
            resposta = "SUCESSO: Login efetuado com sucesso Docente";

            // 2. Se falhar, tentar autenticar como ESTUDANTE
        } else if (dbManager.autenticarEstudante(email, password)) {
            estadoLogin = ESTADO_ESTUDANTE;
            resposta = "SUCESSO: Login efetuado com sucesso Estudante";

            // 3. Falha Total
        } else {
            estadoLogin = ESTADO_INICIAL;
            resposta = "ERRO: Credenciais inválidas (Email ou Password incorretos).";
        }

        out.writeObject(resposta);
        out.flush();
    }

    /**
     * Processa a mensagem de registo (Docente ou Estudante).
     */
    private void processarRegisto(MsgRegisto msg, ObjectOutputStream out) throws IOException {
        String resposta;
        boolean sucesso = false;

        try {
            if (msg.isDocente()) {
                Docente d = msg.getDocente();
                String codUnico = msg.getCodigoDocente();
                System.out.println("[Handler] Tentativa de Registo DOCENTE: " + d.getEmail());

                // TODO: Validação Crítica - Verificar o código único (dbManager.verificarCodigoDocente(codUnico))
                sucesso = dbManager.registarDocente(d);

            } else if (msg.isEstudante()) {
                Estudante e = msg.getEstudante();
                System.out.println("[Handler] Tentativa de Registo ESTUDANTE: " + e.getEmail());

                sucesso = dbManager.registarEstudante(e);

            } else {
                out.writeObject("ERRO: Mensagem de registo inválida ou incompleta.");
                out.flush();
                return;
            }

            if (sucesso) {
                resposta = "SUCESSO: Utilizador registado. Pode agora fazer login.";
            } else {
                resposta = "ERRO: O registo falhou. Credenciais (email/número) já em uso ou código docente inválido.";
            }

        } catch (Exception e) {
            // Captura exceções específicas do registo (ex: DB)
            System.err.println("[Handler] Falha ao tentar registar: " + e.getMessage());
            resposta = "ERRO: Falha interna no servidor ao tentar persistir os dados.";

            // O erro de registo não deve terminar o thread, apenas o loop do cliente.
            estadoLogin = ESTADO_INICIAL;
        }

        out.writeObject(resposta);
        out.flush();
    }
}