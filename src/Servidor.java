import java.io.*;
import java.net.*;

public class Servidor {

    private final String ipDiretorio;
    private final int portoDiretorio;
    private final String dbPath;
    private final String ipMulticast;

    private ServerSocket srvSocketClientes;
    private ServerSocket srvSocketDB;

    private int portoClienteTCP;
    private int portoBDT_TCP;

    private DatabaseManager db;

    public Servidor(String ipDir, int pDir, String dbPath, String ipMulti) {
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.dbPath = dbPath;
        this.ipMulticast = ipMulti;
        System.out.println("[Servidor] A configurar...");
    }

    public void iniciar() {
        try (ServerSocket srvClientes = new ServerSocket(0);
             ServerSocket srvDB = new ServerSocket(0)) {

            this.srvSocketClientes = srvClientes;
            this.srvSocketDB = srvDB;

            this.portoClienteTCP = srvClientes.getLocalPort();
            this.portoBDT_TCP = srvDB.getLocalPort();

            System.out.println("[Servidor] A escutar Clientes TCP no porto: " + portoClienteTCP);

            // 1. Registar no Diretoria
            MsgRespostaDiretoria resposta = registarNoDiretorio();

            if (resposta == null || !resposta.existeServidor()) {
                System.err.println("[Servidor] Falha no registo na Diretoria. A terminar.");
                return;
            }

            // 2. Verificar Identidade
            boolean souEu = (resposta.getPortoClienteTCP() == this.portoClienteTCP);

            if (souEu) {
                System.out.println("[Servidor] >>> MODO PRINCIPAL <<<");
                prepararBaseDeDados();
                aceitarPedidosBD(); // Thread para backups
                aceitarClientes();  // Thread para clientes
            } else {
                System.out.println("[Servidor] >>> MODO BACKUP <<<");
                // Lógica de backup futura...
            }

            // Loop para manter a main thread viva
            System.out.println("[Servidor] Em funcionamento. Pressione Ctrl+C para sair.");
            while(true) { Thread.sleep(10000); }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepararBaseDeDados() {
        System.out.println("[Principal] A ligar à Base de Dados...");
        this.db = new DatabaseManager(this.dbPath);
        try {
            db.conectar();
            db.criarTabelas();
            System.out.println("[Principal] BD pronta e tabelas verificadas.");
        } catch (Exception e) {
            System.err.println("[Principal] Erro fatal na BD: " + e.getMessage());
            System.exit(1);
        }
    }

    // CORREÇÃO CRÍTICA: O loop agora não morre se um cliente falhar
    private void aceitarClientes() {
        new Thread(() -> {
            System.out.println("[Accept] Thread de aceitação de clientes iniciada.");
            while (true) {
                try {
                    Socket s = srvSocketClientes.accept();

                    // Tenta iniciar o handler. Se falhar (ex: streams), apanha a exceção e continua.
                    try {
                        ClientHandler handler = new ClientHandler(s, this.db);
                        new Thread(handler).start();
                    } catch (IOException e) {
                        System.err.println("[Accept] Erro ao iniciar handler para cliente: " + e.getMessage());
                        s.close(); // Fecha o socket estragado
                    }

                } catch (IOException e) {
                    System.err.println("[Accept] Erro fatal no ServerSocket: " + e.getMessage());
                    break; // Se o ServerSocket morrer, sai do loop
                }
            }
        }).start();
    }

    private MsgRespostaDiretoria registarNoDiretorio() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            MsgRegistoServidor msg = new MsgRegistoServidor(portoClienteTCP, portoBDT_TCP);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(msg);
            byte[] data = baos.toByteArray();

            InetAddress ip = InetAddress.getByName(this.ipDiretorio);
            socket.send(new DatagramPacket(data, data.length, ip, this.portoDiretorio));

            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
            return (MsgRespostaDiretoria) ois.readObject();
        } catch (Exception e) {
            System.err.println("[Registo] Erro ao contactar diretoria: " + e.getMessage());
            return null;
        }
    }

    private void aceitarPedidosBD() {
        new Thread(() -> {
            try {
                while (true) {
                    Socket s = srvSocketDB.accept();
                    // Lógica de envio de BD para backups (simplificada para evitar erros agora)
                    s.close();
                }
            } catch (IOException e) {}
        }).start();
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java Servidor <ip_dir> <porto_dir> <bd_file> <multicast_ip>");
            return;
        }
        new Servidor(args[0], Integer.parseInt(args[1]), args[2], args[3]).iniciar();
    }
}