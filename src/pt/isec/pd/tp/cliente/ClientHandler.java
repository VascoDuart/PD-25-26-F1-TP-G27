package pt.isec.pd.tp.cliente;

import pt.isec.pd.tp.DatabaseManager;
import pt.isec.pd.tp.estruturas.*;
import pt.isec.pd.tp.mensagens.*;
import pt.isec.pd.tp.servidor.ServerAPI;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

            clientSocket.setSoTimeout(AUTH_TIMEOUT_MS);


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
            System.out.println("[Handler] pt.isec.pd.tp.cliente.Cliente " + userEmail + " autenticado.");


            while (true) {
                try {
                    Object msg = in.readObject();


                    if (msg instanceof MsgLogout) {
                        enviarObjeto("SUCESSO: Logout efetuado.");
                        return;
                    }


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
                            } else if (msg instanceof MsgObterPergunta) {
                                processarObterPergunta((MsgObterPergunta) msg);
                            } else if (msg instanceof MsgEditarPerfil) {
                                processarEditarPerfil((MsgEditarPerfil) msg);
                            }

                        } else if (estadoLogin == ESTADO_ESTUDANTE) {
                            if (msg instanceof MsgObterPergunta) {
                                processarObterPergunta((MsgObterPergunta) msg);
                            } else if (msg instanceof MsgResponderPergunta) {
                                processarResponderPergunta((MsgResponderPergunta) msg);
                            } else if (msg instanceof MsgObterHistorico) {
                                List<HistoricoItem> historico = dbManager.obterHistoricoEstudante(userId);
                                enviarObjeto(historico);
                            } else if (msg instanceof MsgEditarPerfil) { // NOVO
                                processarEditarPerfil((MsgEditarPerfil) msg);
                            }
                        }
                    } catch (Exception e) {

                        System.err.println("[Handler] Erro ao processar mensagem: " + e.getMessage());

                        enviarObjeto("ERRO INTERNO: Falha na lógica do servidor: " + e.getMessage());
                    }

                } catch (EOFException | SocketException e) {
                    System.out.println("[Handler] pt.isec.pd.tp.cliente.Cliente saiu: " + userEmail);
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



    private void processarLogin(MsgLogin msg) throws IOException {
        if (dbManager.autenticarDocente(msg.getEmail(), msg.getPassword())) {
            estadoLogin = ESTADO_DOCENTE;
            userEmail = msg.getEmail();
            userId = dbManager.obterIdDocente(msg.getEmail());
            enviarObjeto("SUCESSO: Login pt.isec.pd.tp.bases.Docente");
            clientSocket.setSoTimeout(0);
        } else if (dbManager.autenticarEstudante(msg.getEmail(), msg.getPassword())) {
            estadoLogin = ESTADO_ESTUDANTE;
            userEmail = msg.getEmail();
            userId = dbManager.obterIdEstudante(msg.getEmail());
            enviarObjeto("SUCESSO: Login pt.isec.pd.tp.bases.Estudante");
            clientSocket.setSoTimeout(0);
        } else {
            enviarObjeto("ERRO: Credenciais inválidas.");
        }
    }

    private void processarRegisto(MsgRegisto msg) throws IOException {
        String resposta = "ERRO: Falha desconhecida no registo.";

        synchronized (serverAPI.getBDLock()) {
            try {
                if (msg.isDocente()) {
                    if (!dbManager.validarCodigoDocente(msg.getCodigoDocente())) {
                        resposta = "ERRO: Código de docente inválido.";
                    } else {
                        String querySql = dbManager.registarDocente(msg.getDocente());
                        if (querySql != null) {
                            resposta = "SUCESSO: pt.isec.pd.tp.bases.Docente registado!";
                            serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                        } else {
                            resposta = "ERRO: pt.isec.pd.tp.bases.Docente ja registado ou email duplicado.";
                        }
                    }
                } else if (msg.isEstudante()) {
                    String querySql = dbManager.registarEstudante(msg.getEstudante());
                    if (querySql != null) {
                        resposta = "SUCESSO: pt.isec.pd.tp.bases.Estudante registado!";
                        serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                    } else {
                        resposta = "ERRO: Dados inválidos ou duplicados (email/número).";
                    }
                }
            } catch (Exception e) {

                System.err.println("[Handler] Erro de BD/Lógica no registo: " + e.getMessage());
                e.printStackTrace();
                resposta = "ERRO: Falha de servidor ao processar registo. Tente de novo.";
            }
        }

        enviarObjeto(resposta);
    }


    private void processarEditarPerfil(MsgEditarPerfil msg) throws IOException {
        String resposta = "ERRO: Não foi possível editar o perfil.";
        String querySql = null;

        synchronized (serverAPI.getBDLock()) {
            if (msg.isDocente() && estadoLogin == ESTADO_DOCENTE) {
                Docente d = msg.getNovoDocente();

                querySql = dbManager.editarDocente(userEmail, d.getNome(), d.getPassword());
            } else if (msg.isEstudante() && estadoLogin == ESTADO_ESTUDANTE) {
                Estudante e = msg.getNovoEstudante();

                querySql = dbManager.editarEstudante(userEmail, e.getNumEstudante(), e.getNome(), e.getPassword());
            }

            if (querySql != null) {
                resposta = "SUCESSO: Perfil atualizado.";
                serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
            } else {
                resposta = "ERRO: Falha na base de dados ou dados duplicados.";
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



    private void processarObterPergunta(MsgObterPergunta msg) throws IOException {
        Pergunta p = dbManager.obterPerguntaPorCodigo(msg.getCodigoAcesso());

        if (p == null) {

            enviarObjeto("ERRO: pt.isec.pd.tp.bases.Pergunta com código " + msg.getCodigoAcesso() + " não encontrada.");
            return;
        }


        if (estadoLogin == ESTADO_DOCENTE) {
            enviarObjeto(p);
            return;
        }


        if (estadoLogin == ESTADO_ESTUDANTE) {
            if (dbManager.isPerguntaAtiva(p.getCodigoAcesso())) {

                List<Opcao> opcoesFiltradas = p.getOpcoes().stream()
                        .map(o -> new Opcao(o.getLetra(), o.getTexto(), false))
                        .collect(Collectors.toList());


                p.setOpcoes(opcoesFiltradas);

                enviarObjeto(p);
            } else {
                enviarObjeto("ERRO: pt.isec.pd.tp.bases.Pergunta fora do período de disponibilidade.");
            }
        }
    }

    private void processarResponderPergunta(MsgResponderPergunta msg) throws IOException {
        String resposta = "ERRO: Falha (já respondeste ou pergunta invalida?).";



        synchronized (serverAPI.getBDLock()) {

            if (!dbManager.isPerguntaAtiva(msg.getCodigoAcesso())) {
                resposta = "ERRO: pt.isec.pd.tp.bases.Pergunta não está ativa.";
            } else {
                String querySql = dbManager.registarResposta(userId, msg.getCodigoAcesso(), msg.getLetraOpcao());

                if (querySql != null) {
                    resposta = "SUCESSO: Resposta guardada.";
                    serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                } else {
                    resposta = "ERRO: Já respondeste a esta pergunta.";
                }
            }
        }
        enviarObjeto(resposta);
    }


    private void processarObterRespostas(MsgObterRespostas msg) throws IOException {
        String codigo = msg.getCodigoAcesso();
        Pergunta p = dbManager.obterPerguntaPorCodigo(codigo);

        if (p == null) {
            enviarObjeto("ERRO: pt.isec.pd.tp.bases.Pergunta com código " + codigo + " não encontrada.");
            return;
        }

        int donoID = dbManager.obterDocenteIDDaPergunta(codigo);

        if (donoID == -1 || donoID != userId) {
            enviarObjeto("ERRO: Acesso negado. A pergunta não lhe pertence.");
            return;
        }


        if (!dbManager.isPerguntaExpirada(codigo)) {

            enviarObjeto("ERRO: A exportação/consulta só é permitida para perguntas expiradas.");
            return;
        }


        List<RespostaEstudante> resps = dbManager.obterRespostasDaPergunta(codigo);

        if (resps == null) {
            enviarObjeto("ERRO: Falha na base de dados ao obter respostas.");
            return;
        }


        enviarObjeto(resps);
    }

    private void processarEliminarPergunta(MsgEliminarPergunta msg) throws IOException {
        String resposta = "ERRO: Não foi possível eliminar (verifique se tem respostas ou permissões).";

        synchronized (serverAPI.getBDLock()) {
            if (dbManager.podeAlterarPergunta(msg.getCodigoAcesso(), userId)) {
                String querySql = dbManager.eliminarPergunta(msg.getCodigoAcesso());

                if (querySql != null) {
                    resposta = "SUCESSO: pt.isec.pd.tp.bases.Pergunta eliminada.";
                    serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                    serverAPI.notificarTodosClientes("pt.isec.pd.tp.bases.Pergunta removida: " + msg.getCodigoAcesso());
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
                    resposta = "SUCESSO: pt.isec.pd.tp.bases.Pergunta editada.";
                    serverAPI.publicarAlteracao(querySql, dbManager.getVersaoBD());
                }
            }
        }
        enviarObjeto(resposta);
    }


    private void processarObterEstatisticas(MsgObterEstatisticas msg) throws IOException {
        String codigo = msg.getCodigoAcesso();
        int donoId = dbManager.obterDocenteIDDaPergunta(codigo);

        if (donoId == -1 || donoId != userId) {
            enviarObjeto("ERRO: Acesso negado. pt.isec.pd.tp.bases.Pergunta não encontrada ou não lhe pertence.");
            return;
        }

        if (!dbManager.isPerguntaExpirada(codigo)) {
            enviarObjeto("ERRO: Estatísticas apenas disponíveis para perguntas expiradas.");
            return;
        }


        String stats = dbManager.obterEstatisticas(codigo);
        enviarObjeto(stats);
    }
}