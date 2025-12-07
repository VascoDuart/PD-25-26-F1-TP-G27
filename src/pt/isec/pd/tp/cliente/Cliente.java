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


    private static boolean running = true;
    private static int tipoUtilizador = 0;
    private static String ultimoEmail = null;
    private static String ultimaPassword = null;
    private static ServerInfo ultimoServidor = null;
    private static final int TEMPO_RETRY_MS = 20000;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java pt.isec.pd.tp.cliente.Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        while (running) {
            try {

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


                ultimoServidor = servidor;


                if (tipoUtilizador != 0 && ultimoEmail != null) {
                    reautenticarAutomaticamente();
                }


                if (loopInteracao()) {

                    tratarRecuperacaoFalha(ipDir, portoDir);
                }

            } catch (Exception e) {
                vista.mostrarErro("Erro fatal: " + e.getMessage());
            }
        }
    }


    private static boolean loopInteracao() {
        try {
            while (coms.estaConectado()) {

                if (tipoUtilizador == 0) {
                    tratarLogin();
                } else if (tipoUtilizador == 1) {
                    tratarMenuDocente();
                } else if (tipoUtilizador == 2) {
                    tratarMenuEstudante();
                }
            }
        } catch (Exception e) {

            vista.mostrarErro("Comunicação TCP falhou durante a sessão: " + e.getMessage());
            coms.fechar();
            return true;
        }

        coms.fechar();
        return false;
    }



    private static void tratarLogin() {
        int op = vista.menuInicial();
        try {
            if (op == 1) {
                MsgLogin msg = vista.formLogin();


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

        MsgLogin reauthMsg = new MsgLogin(ultimoEmail, ultimaPassword);
        coms.enviar(reauthMsg);

        String resp = (String) coms.receber();
        if (resp.contains("SUCESSO")) {
            vista.mostrarMensagem("Recuperação de falha: Re-autenticação SUCEDIDA. Retomando sessão.");

        } else {
            vista.mostrarErro("Recuperação de falha: Re-autenticação falhou. Login manual necessário.");
            tipoUtilizador = 0;
        }
    }

    private static void tratarRecuperacaoFalha(String ipDir, int portoDir) {
        try {
            vista.mostrarMensagem("A iniciar recuperação de falha do servidor...");


            ServerInfo novoServidor = descobrirServidor(ipDir, portoDir);

            if (novoServidor == null) {
                vista.mostrarErro("Sem Servidores disponíveis para recuperação. A tentar em 20s.");
                Thread.sleep(TEMPO_RETRY_MS);
                return;
            }

            boolean servidorMudou = !novoServidor.equals(ultimoServidor);
            ultimoServidor = novoServidor;

            if (servidorMudou) {

                vista.mostrarMensagem("Novo pt.isec.pd.tp.servidor.Servidor Principal detetado. Tentando reconexão...");


            } else {

                vista.mostrarMensagem("Mesmo servidor offline. Tentando novamente em " + (TEMPO_RETRY_MS / 1000) + "s.");
                Thread.sleep(TEMPO_RETRY_MS);
            }
        } catch (Exception e) {
            vista.mostrarErro("Erro durante a recuperação: " + e.getMessage());
        }
    }




    private static void tratarMenuDocente() {
        int op = vista.menuDocente();
        try {
            if (op == 1) {
                coms.enviar(vista.formCriarPergunta());
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 2) {
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
            else if (op == 3) {
                String codigo = vista.lerTexto("Código da pergunta a editar: ");
                String enunc = vista.lerTexto("Novo Enunciado: ");
                String ini = vista.lerTexto("Novo Início (YYYY-MM-DD HH:MM): ");
                String fim = vista.lerTexto("Novo Fim (YYYY-MM-DD HH:MM): ");

                coms.enviar(new MsgEditarPergunta(codigo, enunc, ini, fim));
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 4) {
                String codigo = vista.lerTexto("Código da pergunta a eliminar: ");
                if (vista.lerTexto("Tem a certeza? (s/n): ").equalsIgnoreCase("s")) {
                    coms.enviar(new MsgEliminarPergunta(codigo));
                    vista.mostrarMensagem((String) coms.receber());
                }
            }
            else if (op == 5) {
                String codigo = vista.lerTexto("Código da pergunta: ");
                coms.enviar(new MsgObterEstatisticas(codigo));
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 6) {
                String codigo = vista.lerTexto("Código da pergunta a exportar: ");
                Pergunta pCompleta = null;
                List<RespostaEstudante> respostas = null;


                coms.enviar(new MsgObterPergunta(codigo));
                Object respP = coms.receber();

                if (respP instanceof Pergunta) {
                    pCompleta = (Pergunta) respP;
                } else {
                    vista.mostrarErro("Falha ao obter pt.isec.pd.tp.bases.Pergunta (Causa: " + respP + ").");
                    return;
                }


                coms.enviar(new MsgObterRespostas(codigo));
                Object respR = coms.receber();

                if (respR instanceof List) {
                    respostas = (List<RespostaEstudante>) respR;
                } else {

                    vista.mostrarErro("Falha ao obter respostas: " + respR);
                    return;
                }


                if (pCompleta != null && respostas != null) {
                    String nomeFicheiro = "resultados_" + codigo + ".csv";
                    ExportadorCSV.exportar(nomeFicheiro, pCompleta, respostas);
                    vista.mostrarMensagem("Ficheiro CSV gerado: " + nomeFicheiro);
                } else {
                    vista.mostrarErro("Dados de exportação incompletos.");
                }
            }
            else if (op == 7) {

                MsgEditarPerfil msg = vista.formEditarPerfil(1, ultimoEmail);
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 0) {
                try {
                    coms.enviar(new MsgLogout());
                } catch (Exception e) {

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
            if (op == 1) {
                String cod = vista.lerTexto("Código: ");
                coms.enviar(new MsgObterPergunta(cod));
                Object resp = coms.receber();
                if (resp instanceof Pergunta) {
                    String letra = vista.formResponderPergunta((Pergunta) resp);
                    coms.enviar(new MsgResponderPergunta(-1, cod, letra));
                    vista.mostrarMensagem((String) coms.receber());
                } else {

                    vista.mostrarErro("Falha ao obter pergunta: " + resp);
                }
            }

            else if (op == 2) {
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
            else if (op == 3) {

                MsgEditarPerfil msg = vista.formEditarPerfil(2, ultimoEmail);
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 0) {
                try {
                    coms.enviar(new MsgLogout());
                } catch (Exception e) {

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