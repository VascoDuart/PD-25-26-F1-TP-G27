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

    private int userId;
    private int estadoLogin = ESTADO_INICIAL;
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
            // Define o timeout inicial para a fase de login/registo
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

            clientSocket.setSoTimeout(0); // Remove o timeout após o login
            System.out.println("[Handler] Cliente " + userEmail + " autenticado.");

            // --- FASE 2: INTERAÇÃO ---
            while (true) {
                try {
                    Object msg = in.readObject();

                    // 1. TRATAMENTO EXPLÍCITO DE LOGOUT
                    if (msg instanceof MsgLogout) {
                        enviarObjeto("SUCESSO: Logout efetuado.");
                        return;
                    }

                    // 2. LÓGICA DE NEGÓCIO (Envolvida em bloco try/catch para garantir resposta)
                    try {
                        if (estadoLogin == ESTADO_DOCENTE) {
                            if (msg instanceof MsgCriarPergunta) {
                                processarCriarPergunta((MsgCriarPergunta) msg);
                            } else if (msg instanceof MsgObterPerguntas) {
                                String filtro = ((MsgObterPerguntas) msg).getFiltro();
                                List<Pergunta> lista = dbManager.listarPerguntasComFiltro(userId, filtro);
                                enviarObjeto(lista);
                            } else if (msg instanceof MsgObterRespostas) {
                                processarObterRespostas((MsgObterRespostas) msg);
                            } else if (msg instanceof MsgEliminarPergunta) {
                                processarEliminarPergunta((MsgEliminarPergunta) msg);
                            } else if (msg instanceof MsgEditarPergunta) {
                                processarEditarPergunta((MsgEditarPergunta) msg);
                            } else if (msg instanceof MsgObterEstatisticas) {
                                processarObterEstatisticas((MsgObterEstatisticas) msg);
                            } else if (msg instanceof MsgObterPergunta) { // Adicionado para Docente
                                processarObterPergunta((MsgObterPergunta) msg);
                            }

                        } else if (estadoLogin == ESTADO_ESTUDANTE) {
                            if (msg instanceof MsgObterPergunta) {
                                processarObterPergunta((MsgObterPergunta) msg);
                            } else if (msg instanceof MsgResponderPergunta) {
                                processarResponderPergunta((MsgResponderPergunta) msg);
                            } else if (msg instanceof MsgObterHistorico) {
                                List<HistoricoItem> historico = dbManager.obterHistoricoEstudante(userId);
                                enviarObjeto(historico);
                            }
                        }
                    } catch (Exception e) {
                        // CAPTURA QUALQUER ERRO DE LÓGICA DE NEGÓCIO
                        System.err.println("[Handler] Erro ao processar mensagem: " + e.getMessage());
                        // CRÍTICO: Envia a resposta de erro ao cliente para prevenir bloqueio
                        enviarObjeto("ERRO INTERNO: Falha na lógica do servidor: " + e.getMessage());
                    }

                } catch (EOFException | SocketException e) {
                    System.out.println("[Handler] Cliente saiu: " + userEmail);
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {}
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

    // --- PROCESSAMENTO DE MENSAGENS DE LÓGICA DE NEGÓCIO ---

    // ... (processarRegisto, processarLogin, processarCriarPergunta - sem alteração na lógica interna)

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


    // CORRIGIDO: Garante resposta se a pergunta não for encontrada (Docente ou Estudante)
    private void processarObterPergunta(MsgObterPergunta msg) throws IOException {
        Pergunta p = dbManager.obterPerguntaPorCodigo(msg.getCodigoAcesso());

        if (p == null) {
            // CORREÇÃO: Responde com ERRO se não encontrada, prevenindo bloqueio na 1ª chamada de Exportação.
            enviarObjeto("ERRO: Pergunta com código " + msg.getCodigoAcesso() + " não encontrada.");
            return;
        }

        // Se é Docente (que está a tentar exportar), enviamos a Pergunta completa sem validações.
        if (estadoLogin == ESTADO_DOCENTE) {
            enviarObjeto(p);
            return;
        }

        // Se é Estudante, validamos o estado e filtramos as opções.
        if (estadoLogin == ESTADO_ESTUDANTE) {
            if (dbManager.isPerguntaAtiva(p.getCodigoAcesso())) {
                // Filtra a opção correta antes de enviar para o estudante (CORREÇÃO DE CONTEÚDO)
                List<Opcao> opcoesFiltradas = p.getOpcoes().stream()
                        .map(o -> new Opcao(o.getLetra(), o.getTexto(), false))
                        .collect(Collectors.toList());

                // NOTA: p.setOpcoes deve existir em Pergunta.
                p.setOpcoes(opcoesFiltradas);

                enviarObjeto(p);
            } else {
                enviarObjeto("ERRO: Pergunta fora do período de disponibilidade.");
            }
        }
    }

    private void processarResponderPergunta(MsgResponderPergunta msg) throws IOException {
        String resposta = "ERRO: Falha (já respondeste ou pergunta invalida?).";

        // A VALIDAÇÃO DE isPerguntaAtiva deve ser feita no DatabaseManager antes de inserir.

        synchronized (serverAPI.getBDLock()) {
            // NOTA: A validação isPerguntaAtiva está no dbManager.registarResposta
            String querySql = dbManager.registarResposta(userId, msg.getCodigoAcesso(), msg.getLetraOpcao());

            if (querySql != null) {
                resposta = "SUCESSO: Resposta guardada.";
                serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
            }
        }
        enviarObjeto(resposta);
    }

    // CORRIGIDO: Lógica de expiração e garantia de resposta (Docente)
    private void processarObterRespostas(MsgObterRespostas msg) throws IOException {
        String codigo = msg.getCodigoAcesso();
        Pergunta p = dbManager.obterPerguntaPorCodigo(codigo); // Carregamos a pergunta primeiro

        if (p == null) {
            enviarObjeto("ERRO: Pergunta com código " + codigo + " não encontrada.");
            return;
        }

        int donoID = dbManager.obterDocenteIDDaPergunta(codigo);

        if (donoID == -1 || donoID != userId) {
            enviarObjeto("ERRO: Acesso negado. A pergunta não lhe pertence.");
            return;
        }

        // CRÍTICO: Verificar se a pergunta expirou para permitir a exportação/consulta.
        if (!dbManager.isPerguntaExpirada(codigo)) {
            // Envia mensagem de erro (String) ao cliente, resolvendo o bloqueio.
            enviarObjeto("ERRO: A exportação/consulta só é permitida para perguntas expiradas.");
            return;
        }

        // Se passou todas as verificações:
        List<RespostaEstudante> resps = dbManager.obterRespostasDaPergunta(codigo);

        if (resps == null) {
            enviarObjeto("ERRO: Falha na base de dados ao obter respostas.");
            return;
        }

        // Envia a lista de respostas.
        enviarObjeto(resps);
    }

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

    // Lógica simplificada de estatísticas (sem redundância)
    private void processarObterEstatisticas(MsgObterEstatisticas msg) throws IOException {
        String codigo = msg.getCodigoAcesso();
        int donoId = dbManager.obterDocenteIDDaPergunta(codigo);

        if (donoId == -1 || donoId != userId) {
            enviarObjeto("ERRO: Acesso negado. Pergunta não encontrada ou não lhe pertence.");
            return;
        }

        if (!dbManager.isPerguntaExpirada(codigo)) {
            enviarObjeto("ERRO: Estatísticas apenas disponíveis para perguntas expiradas.");
            return;
        }

        // Se OK:
        String stats = dbManager.obterEstatisticas(codigo);
        enviarObjeto(stats);
    }
}