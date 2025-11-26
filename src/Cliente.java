import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;

public class Cliente {
    // Instâncias da Vista e Comunicação
    private static final ClienteVista vista = new ClienteVista();
    private static final ClienteComunicacao coms = new ClienteComunicacao(vista);

    // Estado local
    private static boolean running = true;
    private static int tipoUtilizador = 0; // 0-Nenhum, 1-Docente, 2-Estudante

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        // Loop de reconexão (tolerância a falhas)
        while (running) {
            try {
                // 1. Discovery (UDP)
                ServerInfo servidor = descobrirServidor(ipDir, portoDir);
                if (servidor == null) {
                    vista.mostrarErro("Nenhum servidor disponível. A tentar em 5s...");
                    Thread.sleep(5000);
                    continue;
                }

                vista.mostrarMensagem("A conectar a " + servidor.ip + ":" + servidor.porto + "...");

                // 2. Conexão TCP
                if (!coms.conectar(servidor.ip, servidor.porto)) {
                    vista.mostrarErro("Falha na conexão TCP.");
                    Thread.sleep(2000);
                    continue;
                }

                // 3. Loop de Interação
                loopInteracao();

            } catch (Exception e) {
                vista.mostrarErro("Erro fatal no cliente: " + e.getMessage());
            }
        }
    }

    private static void loopInteracao() {
        while (coms.estaConectado()) {
            if (tipoUtilizador == 0) {
                tratarLogin();
            } else if (tipoUtilizador == 1) {
                tratarMenuDocente();
            } else if (tipoUtilizador == 2) {
                tratarMenuEstudante();
            }
        }
        // Se saiu do loop, reseta o estado
        tipoUtilizador = 0;
        vista.mostrarMensagem("Ligação encerrada. A tentar reconectar...");
    }

    private static void tratarLogin() {
        int op = vista.menuInicial();
        try {
            if (op == 1) { // Login
                MsgLogin msg = vista.formLogin();
                coms.enviar(msg);
                String resp = (String) coms.receber(); // Bloqueia à espera
                vista.mostrarMensagem(resp);
                if (resp.contains("Docente")) tipoUtilizador = 1;
                else if (resp.contains("Estudante")) tipoUtilizador = 2;
            }
            else if (op == 2) { // Registo Docente
                MsgRegisto msg = vista.formRegistoDocente();
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 3) { // Registo Estudante
                MsgRegisto msg = vista.formRegistoEstudante();
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 0) {
                running = false;
                coms.fechar();
                System.exit(0);
            }
        } catch (Exception e) {
            vista.mostrarErro("Erro no pedido: " + e.getMessage());
        }
    }

    private static void tratarMenuDocente() {
        int op = vista.menuDocente();
        try {
            if (op == 1) { // Criar Pergunta
                MsgCriarPergunta msg = vista.formCriarPergunta();
                coms.enviar(msg);
                vista.mostrarMensagem((String) coms.receber());
            }
            else if (op == 2) { // Exportar CSV (Fase 2)
                // Isto exigiria lógica extra: Pedir lista de perguntas ao servidor, escolher uma, obter respostas
                // Exemplo simplificado:
                vista.mostrarMensagem("Funcionalidade em construção (requer MsgObterRespostas no protocolo).");
                // Passos:
                // 1. coms.enviar(new MsgObterRespostas(perguntaId));
                // 2. List<RespostaEstudante> lista = (List<RespostaEstudante>) coms.receber();
                // 3. ExportadorCSV.exportar("relatorio.csv", pergunta, lista);
            }
            else if (op == 0) { // Logout
                tipoUtilizador = 0;
            }
        } catch (Exception e) {
            vista.mostrarErro("Erro: " + e.getMessage());
        }
    }

    private static void tratarMenuEstudante() {
        int op = vista.menuEstudante();
        try {
            if (op == 1) { // Responder
                String codigo = vista.lerTexto("Código da Pergunta: ");
                coms.enviar(new MsgObterPergunta(codigo));
                Object resp = coms.receber();

                if (resp instanceof Pergunta) {
                    Pergunta p = (Pergunta) resp;
                    String letra = vista.formResponderPergunta(p);
                    coms.enviar(new MsgResponderPergunta(-1, codigo, letra));
                    vista.mostrarMensagem((String) coms.receber());
                } else {
                    vista.mostrarErro("Pergunta não encontrada.");
                }
            } else if (op == 0) {
                tipoUtilizador = 0;
            }
        } catch (Exception e) {
            vista.mostrarErro("Erro: " + e.getMessage());
        }
    }

    // Auxiliar para Discovery (UDP)
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

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(pkt.getData()));
            MsgRespostaDiretoria msg = (MsgRespostaDiretoria) ois.readObject();

            if (msg.existeServidor()) {
                return new ServerInfo(msg.getIpServidorPrincipal().getHostAddress(), msg.getPortoClienteTCP());
            }
        } catch (Exception e) { /* Ignorar erros de timeout */ }
        return null;
    }

    static class ServerInfo {
        String ip; int porto;
        public ServerInfo(String i, int p) { ip=i; porto=p; }
    }
}