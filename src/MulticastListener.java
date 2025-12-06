import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.*;
import java.sql.SQLException;

public class MulticastListener implements Runnable {

    private final Servidor servidor;
    private final DatabaseManager db;
    private final String ipMulticast;
    private final InetAddress ipLocal;

    private volatile boolean running = true;
    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface nif;

    // Construtor
    public MulticastListener(Servidor s, DatabaseManager db, String ipMulti, InetAddress ipLocal) {
        this.servidor = s;
        this.db = db;
        this.ipMulticast = ipMulti;
        this.ipLocal = ipLocal;
    }

    @Override
    public void run() {
        try {
            this.socket = new MulticastSocket(3030);
            this.group = InetAddress.getByName("230.30.30.30");
            this.nif = NetworkInterface.getByInetAddress(ipLocal);

            // Junta-se ao grupo Multicast
            socket.joinGroup(new InetSocketAddress(group, 3030), nif);

            byte[] buffer = new byte[4096];

            System.out.println("[MulticastListener] A escutar por Heartbeats em 230.30.30.30:3030");

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Ignora heartbeats próprios (para evitar loops)
                if (packet.getAddress().equals(ipLocal)) {
                    continue;
                }

                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()))) {
                    MsgHeartbeat heartbeat = (MsgHeartbeat) ois.readObject();

                    int localVersion = db.getVersaoBD();
                    int remoteVersion = heartbeat.getVersaoBD();

                    // --- Lógica de Sincronização e Tolerância a Falhas (Requisito Crítico) ---

                    if (heartbeat.temQuery()) {
                        // Heartbeat com Query SQL (de Escrita)
                        if (remoteVersion == localVersion + 1) {
                            // Versão sequencial correta: Aplica a query
                            db.executarQueryReplica(heartbeat.getQuerySQL());
                        } else {
                            // Versão incorreta: Perda de sincronização -> TERMINA
                            System.err.println("[MulticastListener] ERRO CRÍTICO: Perda de sincronização com o Principal (Versão Local: " + localVersion + ", Versão Remota: " + remoteVersion + " - Query). A TERMINAR.");
                            System.exit(1);
                        }
                    } else {
                        // Heartbeat Periódico (sem Query)
                        if (remoteVersion != localVersion) {
                            // Versão diferente sem Query: Perda de sincronização -> TERMINA
                            System.err.println("[MulticastListener] ERRO CRÍTICO: Perda de sincronização com o Principal (Versão Local: " + localVersion + ", Versão Remota: " + remoteVersion + " - Periódico). A TERMINAR.");
                            System.exit(1);
                        }
                    }

                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("[MulticastListener] Erro ao processar Heartbeat: " + e.getMessage());
                } catch (SQLException e) {
                    System.err.println("[MulticastListener] ERRO CRÍTICO: Falha ao executar Query de Replicação. A TERMINAR.");
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            System.err.println("[MulticastListener] Erro fatal no Socket Multicast: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public void stop() {
        this.running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}