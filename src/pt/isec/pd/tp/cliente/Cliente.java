package pt.isec.pd.tp.cliente;

import pt.isec.pd.tp.ExportadorCSV;
import pt.isec.pd.tp.estruturas.HistoricoItem;
import pt.isec.pd.tp.estruturas.Pergunta;
import pt.isec.pd.tp.estruturas.RespostaEstudante;
import pt.isec.pd.tp.mensagens.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.List;

public class Cliente {
    private static final ClienteVista vista = new ClienteVista();
    private static final ClienteComunicacao coms = new ClienteComunicacao(vista);

    // Variáveis de Estado (para Recuperação de Falha e Continuação de Sessão)
    private static boolean running = true;
    private static int tipoUtilizador = 0; // 0-Nenhum, 1-pt.isec.pd.tp.bases.Docente, 2-pt.isec.pd.tp.bases.Estudante
    private static String ultimoEmail = null;
    private static String ultimaPassword = null;
    private static ServerInfo ultimoServidor = null; // Último pt.isec.pd.tp.servidor.Servidor Principal conhecido
    private static final int TEMPO_RETRY_MS = 20000; // 20 segundos

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java pt.isec.pd.tp.cliente.Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        while (running) {
            try {
                // 1. Discovery
                ServerInfo servidor = descobrirServidor(ipDir, portoDir);
                if (servidor == null) {
                    vista.mostrarErro("Sem servidores disponíveis. A tentar em 5s...");
                    Thread.sleep(5000);
                    continue;
                }

                vista.mostrarMensagem("A conectar a " + servidor.ip + ":" + servidor.porto);

                if (!coms.conectar(servidor.ip, servidor.porto)) {
                    vista.mostrarErro("Falha TCP na conexão.");
                    Thread.sleep(2000);
                    continue;
                }

                // 2. Conexão bem-sucedida: Atualiza o último servidor conhecido
                ultimoServidor = servidor;

                // 3. Re-autenticação automática (para recuperação de falha)
                if (tipoUtilizador != 0 && ultimoEmail != null) {
                    reautenticarAutomaticamente();
                }

                // 4. Inicia o loop de interação (pode terminar por falha TCP ou logout)
                if (loopInteracao()) {
                    // Retornou true: Houve uma falha inesperada (TCP caiu). Inicia recuperação.
                    tratarRecuperacaoFalha(ipDir, portoDir);
                }

            } catch (Exception e) {
                vista.mostrarErro("Erro fatal: " + e.getMessage());
            }
        }
    }

    /**
     * Loop principal de interação com o usuário.
     * @return true se a sessão terminou por falha de conexão inesperada, false se foi logout.
     */
    private static boolean loopInteracao() {
        try {
            while (coms.estaConectado()) {
                // Se o usuário não está autenticado, força a fase de login/registo
                if (tipoUtilizador == 0) {
                    tratarLogin();
                } else if (tipoUtilizador == 1) {
                    tratarMenuDocente();
                } else if (tipoUtilizador == 2) {
                    tratarMenuEstudante();
                }
            }
        } catch (Exception e) {
            // Falha de I/O na comunicação (socket quebrado).
            // O estado de autenticação (tipoUtilizador, credenciais) é MANTIDO para re-autenticação.
            vista.mostrarErro("Comunicação TCP falhou durante a sessão: " + e.getMessage());
            coms.fechar();
            return true; // Sinaliza para iniciar a recuperação
        }
        // Logout intencional
        coms.fechar();
        return false; // Não há falha a recuperar
    }

    // ==================================================================================
    // LÓGICA DE AUTENTICAÇÃO E RECUPERAÇÃO DE FALHA
    // ... (Métodos tratarLogin, reautenticarAutomaticamente, tratarRecuperacaoFalha são mantidos)
    // ==================================================================================

    private static void tratarLogin() {
        int op = vista.menuInicial();
        try {
            if (op == 1) {
                MsgLogin msg = vista.formLogin();

                // CRÍTICO: Salva as credenciais em texto limpo para re-autenticação automática
                ultimoEmail = msg.getEmail();
                ultimaPassword = msg.getPassword();

                coms.enviar(msg);
                String resp = (String) coms.receber();
                vista.mostrarMensagem(resp);
                if (resp.contains("pt.isec.pd.tp.bases.Docente")) tipoUtilizador = 1;
                else if (resp.contains("pt.isec.pd.tp.bases.Estudante")) tipoUtilizador = 2;

            } else if (op == 2) {
                coms.enviar(vista.formRegistoDocente());
                vista.mostrarMensagem((String) coms.receber());
            } else if (op == 3) {
                coms.enviar(vista.formRegistoEstudante());
                vista.mostrarMensagem((String) coms.receber());
            } else if (op == 0) {
                running = false;
                coms.fechar();
                System.exit(0);
            }
        } catch (Exception e) { vista.mostrarErro("Erro: " + e.getMessage()); }
    }

    private static void reautenticarAutomaticamente() throws Exception {
        // Assume que a conexão já foi estabelecida no main()
        MsgLogin reauthMsg = new MsgLogin(ultimoEmail, ultimaPassword);
        coms.enviar(reauthMsg);

        String resp = (String) coms.receber();
        if (resp.contains("SUCESSO")) {
            vista.mostrarMensagem("Recuperação de falha: Re-autenticação SUCEDIDA. Retomando sessão.");
            // tipoUtilizador é preservado
        } else {
            vista.mostrarErro("Recuperação de falha: Re-autenticação falhou. Login manual necessário.");
            tipoUtilizador = 0; // Força login manual no próximo ciclo
        }
    }

    private static void tratarRecuperacaoFalha(String ipDir, int portoDir) {
        try {
            vista.mostrarMensagem("A iniciar recuperação de falha do servidor...");

            // 1. Tenta descobrir o pt.isec.pd.tp.servidor.Servidor Principal atual
            ServerInfo novoServidor = descobrirServidor(ipDir, portoDir);

            if (novoServidor == null) {
                vista.mostrarErro("Sem Servidores disponíveis para recuperação. A tentar em 20s.");
                Thread.sleep(TEMPO_RETRY_MS);
                return;
            }

            boolean servidorMudou = !novoServidor.equals(ultimoServidor);
            ultimoServidor = novoServidor;

            if (servidorMudou) {
                // CASO 1: SERVIDOR MUDOU (Failover concluído na Diretoria)
                vista.mostrarMensagem("Novo pt.isec.pd.tp.servidor.Servidor Principal detetado. Tentando reconexão...");
                // O loop 'main' continua e irá conectar-se a 'novoServidor' no próximo ciclo.

            } else {
                // CASO 2: MESMO SERVIDOR (Ainda offline - Retry)
                vista.mostrarMensagem("Mesmo servidor offline. Tentando novamente em " + (TEMPO_RETRY_MS / 1000) + "s.");
                Thread.sleep(TEMPO_RETRY_MS);
            }
        } catch (Exception e) {
            vista.mostrarErro("Erro durante a recuperação: " + e.getMessage());
        }
    }


    // ==================================================================================
    // LÓGICA DE MENUS E OPERAÇÕES DE NEGÓCIO (Funcionalidades restauradas)
    // ==================================================================================

    private static void tratarMenuDocente() {
        int op = vista.menuDocente();
        try {
            if (op == 1) { // CRIAR PERGUNTA
                coms.enviar(vista.formCriarPergunta());
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 2) { // CONSULTAR PERGUNTAS
                String filtro = vista.escolherFiltro();
                coms.enviar(new MsgObterPerguntas(filtro));

                Object resp = coms.receber();
                if (resp instanceof List) {
                    List<Pergunta> lista = (List<Pergunta>) resp;
                    if (lista.isEmpty()) {
                        vista.mostrarMensagem("Nenhuma pergunta encontrada para o filtro: " + filtro);
                    } else {
                        System.out.println("\n--- Lista de Perguntas (" + filtro + ") ---");
                        for (Pergunta p : lista) {
                            System.out.printf("[%s] %s (Início: %s | Fim: %s)\n",
                                    p.getCodigoAcesso(), p.getEnunciado(), p.getInicio(), p.getFim());
                        }
                        System.out.println("----------------------------------------");
                    }
                } else {
                    vista.mostrarErro("Erro ao obter lista: " + resp);
                }
            }
            else if (op == 3) { // EDITAR PERGUNTA
                String codigo = vista.lerTexto("Código da pergunta a editar: ");
                String enunc = vista.lerTexto("Novo Enunciado: ");
                String ini = vista.lerTexto("Novo Início (YYYY-MM-DD HH:MM): ");
                String fim = vista.lerTexto("Novo Fim (YYYY-MM-DD HH:MM): ");

                coms.enviar(new MsgEditarPergunta(codigo, enunc, ini, fim));
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 4) { // ELIMINAR PERGUNTA
                String codigo = vista.lerTexto("Código da pergunta a eliminar: ");
                if (vista.lerTexto("Tem a certeza? (s/n): ").equalsIgnoreCase("s")) {
                    coms.enviar(new MsgEliminarPergunta(codigo));
                    vista.mostrarMensagem((String) coms.receber());
                }
            }
            else if (op == 5) { // VER ESTATÍSTICAS
                String codigo = vista.lerTexto("Código da pergunta: ");
                coms.enviar(new MsgObterEstatisticas(codigo));
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 6) { // EXPORTAR CSV
                String codigo = vista.lerTexto("Código da pergunta a exportar: ");
                Pergunta pCompleta = null;
                List<RespostaEstudante> respostas = null;

                // 1. PEDIR PERGUNTA
                coms.enviar(new MsgObterPergunta(codigo));
                Object respP = coms.receber();

                if (respP instanceof Pergunta) {
                    pCompleta = (Pergunta) respP;
                } else {
                    vista.mostrarErro("Falha ao obter pt.isec.pd.tp.bases.Pergunta (Causa: " + respP + ").");
                    return;
                }

                // 2. PEDIR RESPOSTAS
                coms.enviar(new MsgObterRespostas(codigo));
                Object respR = coms.receber();

                if (respR instanceof List) {
                    respostas = (List<RespostaEstudante>) respR;
                } else {
                    // Recebeu um ERRO (String): Acesso negado, Não Expirada, etc.
                    vista.mostrarErro("Falha ao obter respostas: " + respR);
                    return;
                }

                // 3. EXPORTAR
                if (pCompleta != null && respostas != null) {
                    String nomeFicheiro = "resultados_" + codigo + ".csv";
                    ExportadorCSV.exportar(nomeFicheiro, pCompleta, respostas);
                    vista.mostrarMensagem("Ficheiro CSV gerado: " + nomeFicheiro);
                } else {
                    vista.mostrarErro("Dados de exportação incompletos.");
                }
            }
            else if (op == 7) { // EDITAR PERFIL DOCENTE
                // 1 indica pt.isec.pd.tp.bases.Docente. O email atual está em 'ultimoEmail'.
                MsgEditarPerfil msg = vista.formEditarPerfil(1, ultimoEmail);
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 0) { // LOGOUT
                try {
                    coms.enviar(new MsgLogout()); // Sinaliza o pt.isec.pd.tp.servidor.Servidor para fechar
                } catch (Exception e) {
                    // Ignora, a conexão já pode estar a cair
                }

                coms.fechar();
                tipoUtilizador = 0;
                return;
            }
        } catch (Exception e) { vista.mostrarErro("Erro: " + e.getMessage()); }
    }

    private static void tratarMenuEstudante() {
        int op = vista.menuEstudante();
        try {
            if (op == 1) { // RESPONDER PERGUNTA
                String cod = vista.lerTexto("Código: ");
                coms.enviar(new MsgObterPergunta(cod));
                Object resp = coms.receber();
                if (resp instanceof Pergunta) {
                    String letra = vista.formResponderPergunta((Pergunta) resp);
                    coms.enviar(new MsgResponderPergunta(-1, cod, letra));
                    vista.mostrarMensagem((String) coms.receber());
                } else {
                    // Recebeu ERRO String (pt.isec.pd.tp.bases.Pergunta não ativa/encontrada)
                    vista.mostrarErro("Falha ao obter pergunta: " + resp);
                }
            }

            else if (op == 2) { // HISTÓRICO
                coms.enviar(new MsgObterHistorico());
                Object resp = coms.receber();
                if (resp instanceof List) {
                    List<HistoricoItem> lista = (List<HistoricoItem>) resp;
                    if (lista.isEmpty()) {
                        vista.mostrarMensagem("Sem histórico de perguntas expiradas.");
                    } else {
                        System.out.println("\n--- Histórico ---");
                        for (HistoricoItem item : lista) {
                            System.out.println(item);
                        }
                    }
                }
            }
            else if (op == 3) { // EDITAR PERFIL ESTUDANTE
                // 2 indica pt.isec.pd.tp.bases.Estudante. O email atual está em 'ultimoEmail'.
                MsgEditarPerfil msg = vista.formEditarPerfil(2, ultimoEmail);
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 0) {
                try {
                    coms.enviar(new MsgLogout());
                } catch (Exception e) {
                    // Ignora, a conexão já pode estar a cair
                }

                coms.fechar();
                tipoUtilizador = 0;
                return;
            }
        } catch (Exception e) { vista.mostrarErro("Erro: " + e.getMessage()); }
    }


    private static ServerInfo descobrirServidor(String ipDir, int portoDir) {
        try (DatagramSocket udp = new DatagramSocket()) {
            udp.setSoTimeout(5000);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(new MsgPedidoServidor());
            byte[] data = baos.toByteArray();
            udp.send(new DatagramPacket(data, data.length, InetAddress.getByName(ipDir), portoDir));

            byte[] buf = new byte[4096];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            udp.receive(pkt);
            MsgRespostaDiretoria msg = (MsgRespostaDiretoria) new ObjectInputStream(new ByteArrayInputStream(pkt.getData())).readObject();

            if (msg.existeServidor()) return new ServerInfo(msg.getIpServidorPrincipal().getHostAddress(), msg.getPortoClienteTCP());
        } catch (Exception e) {}
        return null;
    }

    // Classe auxiliar de estado do pt.isec.pd.tp.servidor.Servidor
    static class ServerInfo {
        String ip; int porto;
        ServerInfo(String i, int p) { ip=i; porto=p; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ServerInfo other = (ServerInfo) obj;
            return porto == other.porto && ip.equals(other.ip);
        }
    }
}