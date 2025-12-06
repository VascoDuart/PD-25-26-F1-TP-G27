package pt.isec.pd.tp.javaFX;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import pt.isec.pd.tp.mensagens.MsgPedidoServidor;
import pt.isec.pd.tp.mensagens.MsgRespostaDiretoria;

import java.io.*;
import java.net.*;

public class RedeCliente {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String serverIP;
    private int serverPort;

    // Cadeado para impedir que threads misturem pedidos
    private final Object lock = new Object();

    public RedeCliente() {}

    public void descobrirServidor(String ipDiretoria, int portoDiretoria) throws Exception {
        try (DatagramSocket udp = new DatagramSocket()) {
            udp.setSoTimeout(5000);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(new MsgPedidoServidor());
            byte[] data = baos.toByteArray();

            udp.send(new DatagramPacket(data, data.length, InetAddress.getByName(ipDiretoria), portoDiretoria));

            byte[] buf = new byte[4096];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            udp.receive(pkt);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(pkt.getData()));
            MsgRespostaDiretoria msg = (MsgRespostaDiretoria) ois.readObject();

            if (msg.existeServidor()) {
                this.serverIP = msg.getIpServidorPrincipal().getHostAddress();
                this.serverPort = msg.getPortoClienteTCP();
                System.out.println("[Rede] pt.isec.pd.tp.servidor.Servidor: " + serverIP + ":" + serverPort);
            } else {
                throw new Exception("Diretoria diz: Nenhum servidor ativo.");
            }
        }
    }

    public void conectarTCP() throws IOException {
        if (serverIP == null) throw new IOException("Corre descobrirServidor() primeiro.");
        this.socket = new Socket(serverIP, serverPort);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
        System.out.println("[Rede] TCP conectado.");
    }

    /**
     * Método ATÓMICO e SINCRONIZADO para fazer pedidos.
     * Garante que o pedido e a resposta não se misturam com outras threads.
     */
    public Object enviarEReceber(Object pedido) throws Exception {
        synchronized (lock) {
            if (out == null || in == null) throw new IOException("Não conectado.");

            // 1. Enviar
            out.writeObject(pedido);
            out.flush();

            // 2. Receber (filtrando notificações que apareçam no meio)
            while (true) {
                Object resposta = in.readObject();

                // Se for uma notificação, mostramos mas continuamos à espera da resposta real
                if (resposta instanceof String && ((String) resposta).startsWith("NOTIFICACAO:")) {
                    String msgNotif = ((String) resposta).substring(12);
                    System.out.println("[Notificação] " + msgNotif);
                    // Opcional: Mostrar popup na GUI (tem de ser em Platform.runLater)
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Aviso do Sistema");
                        alert.setHeaderText(null);
                        alert.setContentText(msgNotif);
                        alert.show();
                    });
                    continue; // Volta a ler o próximo objeto
                }

                // Se não for notificação, é a nossa resposta!
                return resposta;
            }
        }
    }

    // Métodos antigos mantidos por compatibilidade (mas evita usá-los diretamente)
    public void enviar(Object msg) throws IOException {
        synchronized (lock) {
            out.writeObject(msg);
            out.flush();
        }
    }

    public Object receber() throws IOException, ClassNotFoundException {
        synchronized (lock) {
            return in.readObject();
        }
    }

    public void fechar() {
        try { if (socket != null) socket.close(); } catch (Exception e) {}
    }
}