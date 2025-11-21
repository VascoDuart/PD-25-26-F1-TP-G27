import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ClientHandler implements Runnable {

    // Constantes de Estado do Cliente
    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;
    private int estadoLogin = ESTADO_INICIAL;

    private int userId; // ID do Docente ou Estudante autenticado
    private String userEmail; // Email do utilizador autenticado

    private final Socket clientSocket;
    private final DatabaseManager dbManager;

    private static final int AUTH_TIMEOUT_MS = 30000;
    // Formato de data/hora a ser usado na BD (YYYY-MM-DD HH:MM:SS)
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            clientSocket.setSoTimeout(AUTH_TIMEOUT_MS);

            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            // 1. Loop de autenticação/registo
            while (estadoLogin == ESTADO_INICIAL) {
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

            // Cliente autenticado. Removemos o timeout e entramos no loop principal.
            clientSocket.setSoTimeout(0);
            System.out.println("[Handler] Cliente " + userEmail + " autenticado. A iniciar interação.");

            // 2. Loop principal de interação
            processarInteracoes(in, out);

        } catch (SocketTimeoutException e) {
            System.err.println("[Handler] Cliente excedeu o tempo limite de autenticação/registo (30s). Fechando.");
        } catch (EOFException | SocketException e) {
            System.out.println("[Handler] Conexão TCP fechada pelo cliente ou Servidor.");
        } catch (Exception e) {
            System.err.println("[Handler] ERRO INESPERADO no thread do cliente: " + e.getMessage());
            e.printStackTrace();
        } finally {
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

    // --- LÓGICA DE INTERAÇÃO APÓS LOGIN ---

    private void processarInteracoes(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        while (clientSocket.isConnected()) {
            // Ler comandos do cliente (enquanto não houver logout ou erro)
            Object msg = in.readObject();

            if (estadoLogin == ESTADO_DOCENTE) {
                if (msg instanceof MsgCriarPergunta) {
                    processarCriarPergunta((MsgCriarPergunta) msg, out);
                }
                // TODO: Adicionar processamento para MsgEditarPergunta, MsgListarPerguntas, etc.
            }
            // TODO: Adicionar lógica para comandos de Estudante (ex: MsgResponderPergunta)

            // Exemplo de comando de logout
            // if (msg instanceof MsgLogout) break;
        }
    }

    // --- PROCESSAMENTO DE MENSAGENS DE INÍCIO DE SESSÃO ---

    private void processarLogin(MsgLogin msg, ObjectOutputStream out) throws IOException {
        String email = msg.getEmail();
        String password = msg.getPassword();
        String resposta;

        // 1. Tentar autenticar como DOCENTE
        if (dbManager.autenticarDocente(email, password)) {
            estadoLogin = ESTADO_DOCENTE;
            userEmail = email;
            // CRÍTICO: Obter o ID para usar nas operações de negócio
            userId = dbManager.obterIdDocente(email);
            resposta = "SUCESSO: Login efetuado com sucesso Docente";

            // 2. Tentar autenticar como ESTUDANTE
        } else if (dbManager.autenticarEstudante(email, password)) {
            estadoLogin = ESTADO_ESTUDANTE;
            userEmail = email;
            // CRÍTICO: Obter o ID
            userId = dbManager.obterIdEstudante(email);
            resposta = "SUCESSO: Login efetuado com sucesso Estudante";

            // 3. Falha Total
        } else {
            estadoLogin = ESTADO_INICIAL;
            userId = -1;
            userEmail = null;
            resposta = "ERRO: Credenciais inválidas (Email ou Password incorretos).";
        }

        out.writeObject(resposta);
        out.flush();
    }

    private void processarRegisto(MsgRegisto msg, ObjectOutputStream out) throws IOException {
        String resposta;
        boolean sucesso = false;

        try {
            if (msg.isDocente()) {
                Docente d = msg.getDocente();
                String codUnico = msg.getCodigoDocente();
                System.out.println("[Handler] Tentativa de Registo DOCENTE: " + d.getEmail());

                // TODO: Validação Crítica - Implementar dbManager.verificarCodigoDocente(codUnico)
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
            System.err.println("[Handler] Falha ao tentar registar: " + e.getMessage());
            resposta = "ERRO: Falha interna no servidor ao tentar persistir os dados.";
            estadoLogin = ESTADO_INICIAL;
        }

        out.writeObject(resposta);
        out.flush();
    }

    // --- LÓGICA DE NEGÓCIO DO DOCENTE ---

    private void processarCriarPergunta(MsgCriarPergunta msg, ObjectOutputStream out) throws IOException {
        String resposta;

        // 1. Gera Código de Acesso Único
        // Código de acesso de 6 caracteres maiúsculos (Exemplo simples, pode necessitar de verificação de colisão)
        String codAcesso = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 2. Formatar Datas
        String inicio = msg.getInicio();
        String fim = msg.getFim();

        // Assumindo que o cliente envia as datas no formato correto (YYYY-MM-DD HH:MM:SS)
        // TODO: Adicionar validação de formato e lógica para garantir que a data de fim > data de início.

        try {
            // 3. Persistir no BD (Função criada no DatabaseManager)
            boolean sucesso = dbManager.criarPergunta(
                    userId, // Usa o ID obtido no login
                    msg.getEnunciado(),
                    codAcesso,
                    inicio,
                    fim,
                    msg.getOpcoes()
            );

            if (sucesso) {
                resposta = "SUCESSO: Pergunta criada. Código de acesso: " + codAcesso;
                // TODO: Propagar a atualização da BD via Multicast/Heartbeat (versão++ e query)
            } else {
                resposta = "ERRO: Falha ao criar a pergunta. Mínimo de 2 opções ou erro de BD.";
            }

        } catch (Exception e) {
            System.err.println("[Handler] Erro ao criar pergunta: " + e.getMessage());
            resposta = "ERRO: Falha interna ao persistir a pergunta.";
        }

        out.writeObject(resposta);
        out.flush();
    }
}