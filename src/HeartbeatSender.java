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

    public HeartbeatSender(Servidor s, DatabaseManager db, String ipDir, int pDir, int pCliente, int pDB, InetAddress ipLocal) {
        this.servidor = s;
        this.db = db;
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.portoClienteTCP = pCliente;
        this.portoBDT_TCP = pDB;
        this.ipLocal = ipLocal;
    }

    public void stop() {
        running = false;
    }

    /**
     * Dispara um Heartbeat de escrita imediatamente na próxima iteração do loop.
     */
    public void dispararHeartbeatEscrita(String querySQL, int novaVersao) {
        this.pendingQuery = querySQL;
        this.pendingVersion = novaVersao;
    }

    @Override
    public void run() {
        try (DatagramSocket socketDir = new DatagramSocket()) {
            // Definir um timeout para a resposta da Diretoria (para não bloquear o loop Heartbeat)
            socketDir.setSoTimeout(3000);

            while (running) {
                Thread.sleep(5000); // Espera 5 segundos

                String queryToSend = null;
                int version = db.getVersaoBD();

                // Se houver uma query pendente (Heartbeat de Escrita)
                if (pendingQuery != null) {
                    queryToSend = pendingQuery;
                    version = pendingVersion;
                    // Reset para o próximo Heartbeat
                    pendingQuery = null;
                    pendingVersion = -1;
                }

                MsgHeartbeat heartbeat = new MsgHeartbeat(version, portoClienteTCP, portoBDT_TCP, queryToSend);
                byte[] data = serializar(heartbeat);

                // 1. Envio para a Diretoria (UDP Unicast)
                socketDir.send(new DatagramPacket(data, data.length, InetAddress.getByName(ipDiretorio), portoDiretorio));

                // 2. RECEBER RESPOSTA E VERIFICAR PROMOÇÃO (CRÍTICO)
                byte[] buffer = new byte[4096];
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

                try {
                    socketDir.receive(responsePacket);
                    processarRespostaDiretoria(responsePacket, socketDir); // Processa a resposta
                } catch (SocketTimeoutException e) {
                    System.out.println("[HeartbeatSender] Timeout ao esperar resposta da Diretoria (esperado).");
                }

                // 3. Envio para o Cluster (UDP Multicast) - Apenas se formos o Principal ou Backup
                enviarMulticast(data);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("[HeartbeatSender] Erro de I/O: " + e.getMessage());
        }
    }

    private void processarRespostaDiretoria(DatagramPacket packet, DatagramSocket socketDir) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()))) {
            MsgRespostaDiretoria resposta = (MsgRespostaDiretoria) ois.readObject();

            if (!servidor.isPrincipal()) { // Se o servidor atual é um Backup

                // 1. Verificar se a Diretoria nos indica como o NOVO Principal
                boolean somosONovoPrincipal =
                        resposta.getIpServidorPrincipal().equals(servidor.getIPLocal()) &&
                                resposta.getPortoClienteTCP() == servidor.getPortoClienteTCP();

                if (somosONovoPrincipal) {
                    // 2. Disparar a Ativação do Serviço (Mudar o papel)
                    System.out.println("[HeartbeatSender] PROMOÇÃO DETETADA PELA DIRETORIA. Ativando serviços...");
                    servidor.ativarServicosPrincipal();
                }
            }
        } catch (Exception e) {
            System.err.println("[HeartbeatSender] Erro ao processar resposta da Diretoria: " + e.getMessage());
        }
    }


    private void enviarMulticast(byte[] data) {
        try (MulticastSocket socketMulti = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName("230.30.30.30"); // Endereço fixo

            // Requisito: Ligação Multicast através da interface local
            NetworkInterface nif = NetworkInterface.getByInetAddress(ipLocal);
            socketMulti.setNetworkInterface(nif);

            DatagramPacket packet = new DatagramPacket(data, data.length, group, 3030); // Porto fixo
            socketMulti.send(packet);
        } catch (IOException e) {
            System.err.println("[HeartbeatSender] Falha no envio Multicast: " + e.getMessage());
        }
    }

    private byte[] serializar(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(obj);
        return baos.toByteArray();
    }
}