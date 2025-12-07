package pt.isec.pd.tp;

import pt.isec.pd.tp.mensagens.MsgHeartbeat;
import pt.isec.pd.tp.mensagens.MsgRespostaDiretoria;
import pt.isec.pd.tp.servidor.Servidor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.*;

public class HeartbeatSender implements Runnable {

    private final Servidor servidor;
    private final DatabaseManager db;
    private final String ipDiretorio;
    private final int portoDiretorio;
    private final int portoClienteTCP;
    private final int portoBDT_TCP;
    private final InetAddress ipLocal;

    private volatile boolean running = true;
    private volatile String pendingQuery = null;
    private volatile int pendingVersion = -1;
    private volatile boolean isPrincipal = false;

    public HeartbeatSender(Servidor s, DatabaseManager db, String ipDir, int pDir, int pCliente, int pDB, InetAddress ipLocal) {
        this.servidor = s;
        this.db = db;
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.portoClienteTCP = pCliente;
        this.portoBDT_TCP = pDB;
        this.ipLocal = ipLocal;
    }

    public void stop() { running = false; }
    public void updateRole(boolean isPrincipal) { this.isPrincipal = isPrincipal; }

    public void dispararHeartbeatEscrita(String querySQL, int novaVersao) {
        this.pendingQuery = querySQL;
        this.pendingVersion = novaVersao;
    }

    @Override
    public void run() {
        try (DatagramSocket socketDir = new DatagramSocket()) {
            socketDir.setSoTimeout(3000);

            while (running) {
                Thread.sleep(5000);

                String queryToSend = null;
                int version = db.getVersaoBD();

                if (pendingQuery != null) {
                    queryToSend = pendingQuery;
                    version = pendingVersion;
                    pendingQuery = null;
                    pendingVersion = -1;
                }

                MsgHeartbeat heartbeat = new MsgHeartbeat(version, portoClienteTCP, portoBDT_TCP, queryToSend);
                byte[] data = serializar(heartbeat);


                socketDir.send(new DatagramPacket(data, data.length, InetAddress.getByName(ipDiretorio), portoDiretorio));


                byte[] buffer = new byte[4096];
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

                try {
                    socketDir.receive(responsePacket);
                    processarRespostaDiretoria(responsePacket, socketDir);
                } catch (SocketTimeoutException e) {

                }


                if (this.isPrincipal) {
                    enviarMulticast(data);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("[pt.isec.pd.tp.HeartbeatSender] Erro de I/O: " + e.getMessage());
        }
    }

    private void processarRespostaDiretoria(DatagramPacket packet, DatagramSocket socketDir) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()))) {
            MsgRespostaDiretoria resposta = (MsgRespostaDiretoria) ois.readObject();


            boolean somosONovoPrincipal =
                    resposta.existeServidor() &&
                            resposta.getIpServidorPrincipal().equals(servidor.getIPLocal()) &&
                            resposta.getPortoClienteTCP() == servidor.getPortoClienteTCP();

            if (!servidor.isPrincipal() && somosONovoPrincipal) {
                servidor.ativarServicosPrincipal();
            }
        } catch (Exception e) {
            System.err.println("[pt.isec.pd.tp.HeartbeatSender] Erro ao processar resposta da Diretoria: " + e.getMessage());
        }
    }

    private void enviarMulticast(byte[] data) {
        try (MulticastSocket socketMulti = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName("230.30.30.30");

            NetworkInterface nif = NetworkInterface.getByInetAddress(ipLocal);
            socketMulti.setNetworkInterface(nif);

            DatagramPacket packet = new DatagramPacket(data, data.length, group, 3030);
            socketMulti.send(packet);
        } catch (IOException e) {
            System.err.println("[pt.isec.pd.tp.HeartbeatSender] Falha no envio Multicast: " + e.getMessage());
        }
    }

    private byte[] serializar(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(obj);
        return baos.toByteArray();
    }
}