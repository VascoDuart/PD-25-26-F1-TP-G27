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
            // 1. Perguntar ao Diretoria onde está o Servidor (UDP)
            System.out.println("[Cliente] A procurar servidor...");
            DatagramSocket udpSocket = new DatagramSocket();
            MsgPedidoServidor pedido = new MsgPedidoServidor();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(pedido);
            byte[] data = baos.toByteArray();

            udpSocket.send(new DatagramPacket(data, data.length, InetAddress.getByName(ipDir), portoDir));

            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
            MsgRespostaDiretoria resposta = (MsgRespostaDiretoria) ois.readObject();
            udpSocket.close();

            if (!resposta.existeServidor()) {
                System.out.println("[Cliente] Nenhum servidor disponível.");
                return;
            }

            System.out.println("[Cliente] Servidor encontrado em " + resposta.getPortoClienteTCP());

            // 2. Ligar ao Servidor TCP e Tentar Registar
            try (Socket socket = new Socket(resposta.getIpServidorPrincipal(), resposta.getPortoClienteTCP());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                System.out.println("[Cliente] Conectado! A tentar registar docente...");

                // --- CRIAR DADOS DE TESTE ---
                // Podes mudar estes valores para testar erros (ex: tentar registar o mesmo email 2 vezes)
                Docente novoDocente = new Docente("Jose Marinho", "jose@isec.pt", "12345");
                MsgRegisto msg = new MsgRegisto(novoDocente);

                // Enviar pedido
                out.writeObject(msg);
                out.flush();

                // Ler resposta
                String servResposta = (String) in.readObject();
                System.out.println("[Servidor Diz]: " + servResposta);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}