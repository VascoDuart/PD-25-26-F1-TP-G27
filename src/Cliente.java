import java.io.*;
import java.net.*;

public class Cliente {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Sintaxe: java Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        try {
            // 1. Obter endereço do Servidor Principal (UDP)
            System.out.println("[Cliente] A perguntar ao Diretoria onde está o servidor...");
            DatagramSocket udpSocket = new DatagramSocket();

            // Prepara a mensagem
            MsgPedidoServidor pedido = new MsgPedidoServidor();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(pedido);
            byte[] data = baos.toByteArray();

            // Envia
            InetAddress ipDiretoria = InetAddress.getByName(ipDir);
            udpSocket.send(new DatagramPacket(data, data.length, ipDiretoria, portoDir));

            // Recebe resposta
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
            MsgRespostaDiretoria resposta = (MsgRespostaDiretoria) ois.readObject();
            udpSocket.close();

            if (!resposta.existeServidor()) {
                System.out.println("[Cliente] O Diretoria diz que não há servidores ativos.");
                return;
            }

            System.out.println("[Cliente] O Servidor é: " + resposta.getIpServidorPrincipal() + ":" + resposta.getPortoClienteTCP());

            // 2. Ligar ao Servidor (TCP)
            try (Socket socket = new Socket(resposta.getIpServidorPrincipal(), resposta.getPortoClienteTCP());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                System.out.println("[Cliente] Conectado com sucesso!");

                // 3. Ler a mensagem de boas-vindas
                String mensagem = (String) in.readObject();
                System.out.println("[Servidor Disse]: " + mensagem);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}