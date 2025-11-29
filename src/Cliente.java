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
    private static int tipoUtilizador = 0; // 0-Nenhum, 1-Docente, 2-Estudante

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        while (running) {
            try {
                // Discovery
                ServerInfo servidor = descobrirServidor(ipDir, portoDir);
                if (servidor == null) {
                    vista.mostrarErro("Sem servidores disponíveis. A tentar em 5s...");
                    Thread.sleep(5000);
                    continue;
                }

                vista.mostrarMensagem("A conectar a " + servidor.ip + ":" + servidor.porto);

                if (!coms.conectar(servidor.ip, servidor.porto)) {
                    vista.mostrarErro("Falha TCP.");
                    Thread.sleep(2000);
                    continue;
                }

                loopInteracao();

            } catch (Exception e) {
                vista.mostrarErro("Erro fatal: " + e.getMessage());
            }
        }
    }

    private static void loopInteracao() {
        while (coms.estaConectado()) {
            if (tipoUtilizador == 0) tratarLogin();
            else if (tipoUtilizador == 1) tratarMenuDocente();
            else if (tipoUtilizador == 2) tratarMenuEstudante();
        }
        tipoUtilizador = 0;
        vista.mostrarMensagem("Ligação perdida.");
    }

    private static void tratarLogin() {
        int op = vista.menuInicial();
        try {
            if (op == 1) {
                MsgLogin msg = vista.formLogin();
                coms.enviar(msg);
                String resp = (String) coms.receber();
                vista.mostrarMensagem(resp);
                if (resp.contains("Docente")) tipoUtilizador = 1;
                else if (resp.contains("Estudante")) tipoUtilizador = 2;
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

    private static void tratarMenuDocente() {
        int op = vista.menuDocente();
        try {
            if (op == 1) {
                coms.enviar(vista.formCriarPergunta());
                vista.mostrarMensagem((String) coms.receber());
            }
            // --- NOVO: EXPORTAR CSV ---
            else if (op == 2) { // Exportar Resultados (CSV)
                try {
                    // 1. Listar perguntas (Resumidas)
                    coms.enviar(new MsgObterPerguntas());
                    Object resp = coms.receber();

                    if (resp instanceof List) {
                        List<Pergunta> lista = (List<Pergunta>) resp;
                        if (lista.isEmpty()) {
                            vista.mostrarErro("Não tem perguntas criadas.");
                        } else {
                            vista.mostrarListaPerguntas(lista);
                            String codigo = vista.lerTexto("Código da pergunta a exportar: ");

                            // CORREÇÃO AQUI: Pedir a pergunta COMPLETA ao servidor
                            // A pergunta da lista 'lista' não tem opções, por isso pedimos de novo
                            coms.enviar(new MsgObterPergunta(codigo)); // Usa MsgObterPergunta (singular)
                            Object respPergunta = coms.receber();

                            if (respPergunta instanceof Pergunta) {
                                Pergunta pCompleta = (Pergunta) respPergunta;

                                // Agora pede as respostas dos alunos
                                coms.enviar(new MsgObterRespostas(codigo));
                                Object respLista = coms.receber();

                                if (respLista instanceof List) {
                                    List<RespostaEstudante> respostas = (List<RespostaEstudante>) respLista;

                                    String nomeFicheiro = "resultados_" + codigo + ".csv";
                                    // Passa a pCompleta que já tem as opções carregadas!
                                    ExportadorCSV.exportar(nomeFicheiro, pCompleta, respostas);
                                    vista.mostrarMensagem("Ficheiro CSV gerado: " + nomeFicheiro);
                                }
                            } else {
                                vista.mostrarErro("Pergunta não encontrada ou código inválido.");
                            }
                        }
                    }
                } catch (Exception e) {
                    vista.mostrarErro("Erro na exportação: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            else if (op == 0) tipoUtilizador = 0;
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
                    vista.mostrarErro("Pergunta não encontrada.");
                }
            } else if (op == 0) tipoUtilizador = 0;
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
    }
}