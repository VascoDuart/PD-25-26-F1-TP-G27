import java.io.*;
import java.net.*;


public class ServicoDeDiretoria {

    // Guarda quem é o chefe (em memória)
    private static InetAddress ipPrincipal = null;
    private static int portoClientePrincipal = -1;
    private static int portoDbPrincipal = -1;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java ServicoDeDiretoria <porto_udp>");
            return;
        }

        int porto = Integer.parseInt(args[0]);

        try (DatagramSocket socket = new DatagramSocket(porto)) {
            System.out.println("[Diretoria] A escutar no porto " + porto + "...");

            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()))) {
                    Object msg = ois.readObject();

                    // --- CASO 1: É UM SERVIDOR A REGISTAR-SE ---
                    if (msg instanceof MsgRegistoServidor) {
                        MsgRegistoServidor registo = (MsgRegistoServidor) msg;
                        System.out.println("[Diretoria] SERVIDOR registou-se: " + packet.getAddress());

                        if (ipPrincipal == null) {
                            ipPrincipal = packet.getAddress();
                            portoClientePrincipal = registo.getPortoClienteTCP();
                            portoDbPrincipal = registo.getPortoBDT_TCP();
                            System.out.println(" -> Novo Principal definido: " + ipPrincipal + ":" + portoClientePrincipal);
                        }

                        enviarResposta(socket, packet.getAddress(), packet.getPort());
                    }

                    // --- CASO 2: É UM CLIENTE A PEDIR O SERVIDOR (ESTE FALTAVA!) ---
                    else if (msg instanceof MsgPedidoServidor) {
                        System.out.println("[Diretoria] CLIENTE pediu servidor: " + packet.getAddress());
                        enviarResposta(socket, packet.getAddress(), packet.getPort());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Método auxiliar para responder (evita repetir código)
    private static void enviarResposta(DatagramSocket socket, InetAddress ipDestino, int portoDestino) throws IOException {
        MsgRespostaDiretoria resposta = new MsgRespostaDiretoria(
                ipPrincipal, portoClientePrincipal, portoDbPrincipal
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(resposta);
        byte[] dataResp = baos.toByteArray();

        DatagramPacket respPacket = new DatagramPacket(
                dataResp, dataResp.length, ipDestino, portoDestino
        );
        socket.send(respPacket);
    }
}