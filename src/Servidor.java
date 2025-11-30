import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// O Servidor implementa ServerAPI para que o ClientHandler possa comunicar alterações.
public class Servidor implements ServerAPI {

    private final String ipDiretorio;
    private final int portoDiretorio;
    private final String dbPath;
    private final String ipMulticast;

    private ServerSocket srvSocketClientes;
    private ServerSocket srvSocketDB;

    private int portoClienteTCP;
    private int portoBDT_TCP;
    private InetAddress ipLocal;

    private DatabaseManager db;
    private HeartbeatSender heartbeatSender;
    private final List<ClientHandler> clientesConectados = Collections.synchronizedList(new ArrayList<>());
    private final Object BD_LOCK = new Object();

    private boolean isPrincipal = false;

    public Servidor(String ipDir, int pDir, String dbPath, String ipMulti) {
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.dbPath = dbPath;
        this.ipMulticast = ipMulti;
        System.out.println("[Servidor] A configurar...");
    }

    public void iniciar() {
        try {
            // 1. Obter IP da interface de rede local (requerido para Multicast)
            this.ipLocal = InetAddress.getLocalHost();

            try (ServerSocket srvClientes = new ServerSocket(0);
                 ServerSocket srvDB = new ServerSocket(0)) {

                this.srvSocketClientes = srvClientes;
                this.srvSocketDB = srvDB;

                this.portoClienteTCP = srvClientes.getLocalPort();
                this.portoBDT_TCP = srvDB.getLocalPort();

                System.out.println("[Servidor] A escutar Clientes TCP no porto: " + portoClienteTCP);

                // 2. Preparar e Ligar à BD antes do registo
                prepararBaseDeDados();

                // 3. Registar no Diretoria e identificar o modo
                MsgRespostaDiretoria resposta = registarNoDiretorio();

                if (resposta == null || !resposta.existeServidor()) {
                    System.err.println("[Servidor] Falha no registo na Diretoria. A terminar.");
                    fecharRecursos();
                    return;
                }

                // 4. Determinar se somos o Principal
                // Comparar o nosso porto TCP com o porto do Principal fornecido pela Diretoria.
                this.isPrincipal = (resposta.getPortoClienteTCP() == this.portoClienteTCP);

                // 5. Iniciar Threads

                // Thread principal: envia Heartbeats periódicos e de escrita (todos os modos)
                this.heartbeatSender = new HeartbeatSender(this, db, ipDiretorio, portoDiretorio, portoClienteTCP, portoBDT_TCP, ipLocal);
                new Thread(this.heartbeatSender).start();

                // Thread de escuta: monitoriza o Multicast (todos os modos)
                new Thread(new MulticastListener(this, db, ipMulticast, ipLocal)).start();

                if (this.isPrincipal) {
                    System.out.println("[Servidor] >>> MODO PRINCIPAL <<<");
                    aceitarPedidosBD(); // Aceita Backups que querem a BD
                    aceitarClientes();  // Aceita Clientes
                } else {
                    System.out.println("[Servidor] >>> MODO BACKUP <<<");
                    receberCopiaBD(resposta.getIpServidorPrincipal(), resposta.getPortoBDT_TCP());
                }

                // Loop para manter a main thread viva
                System.out.println("[Servidor] Em funcionamento. Pressione Ctrl+C para sair.");
                while(true) { Thread.sleep(10000); }

            }
        } catch (InterruptedException e) {
            System.out.println("[Servidor] Thread principal interrompida.");
        } catch (Exception e) {
            System.err.println("[Servidor] Erro fatal durante a inicialização: " + e.getMessage());
            e.printStackTrace();
        } finally {
            fecharRecursos();
        }
    }

    private void fecharRecursos() {
        if (heartbeatSender != null) {
            heartbeatSender.stop(); // Interrompe a thread Heartbeat
        }
        try { srvSocketClientes.close(); } catch (Exception e) {}
        try { srvSocketDB.close(); } catch (Exception e) {}
        if (db != null) db.desconectar();
        // NOTA: Devemos também enviar uma MsgLogoutServidor à Diretoria aqui.
    }

    private void prepararBaseDeDados() throws Exception {
        this.db = new DatabaseManager(this.dbPath);
        db.conectar();
        db.criarTabelas(); // Cria BD se não existir (ou versão 0)
        System.out.println("[Principal] BD pronta e tabelas verificadas.");
    }

    // --- THREAD DE ACEITAÇÃO DE CLIENTES (TCP) ---
    private void aceitarClientes() {
        new Thread(() -> {
            System.out.println("[Accept] Thread de aceitação de clientes iniciada.");
            while (true) {
                try {
                    Socket s = srvSocketClientes.accept();
                    try {
                        // Passar a referência do Servidor (this), que implementa ServerAPI
                        ClientHandler handler = new ClientHandler(s, this.db, this);
                        clientesConectados.add(handler);
                        new Thread(handler).start();
                    } catch (IOException e) {
                        System.err.println("[Accept] Erro ao iniciar handler para cliente: " + e.getMessage());
                        s.close();
                    }
                } catch (IOException e) {
                    System.err.println("[Accept] Erro fatal no ServerSocket: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    public Object getBDLock() {
        return BD_LOCK;
    }

    // --- THREAD DE ACEITAÇÃO DE PEDIDOS DE BD (TCP) ---
    private void aceitarPedidosBD() {
        new Thread(() -> {
            System.out.println("[BD Accept] Thread de aceitação de Backups (BD) iniciada.");
            while (true) {
                try {
                    Socket s = srvSocketDB.accept();

                    synchronized (BD_LOCK) {
                        System.out.println("[BD Accept] BLOQUEIO DE ESCRITA ATIVO. Enviando BD.");
                        File dbFile = new File(dbPath);

                        try (FileInputStream fis = new FileInputStream(dbFile);
                             OutputStream os = s.getOutputStream()) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                            os.flush();
                            System.out.println("[BD Accept] BD enviada com sucesso.");
                        }
                    }
                    s.close();
                } catch (IOException e) {
                    System.err.println("[BD Accept] Erro fatal no ServerSocket BD: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    private void receberCopiaBD(InetAddress ipPrincipal, int portoDBPrincipal) {
        System.out.println("[Backup] Solicitando BD a " + ipPrincipal + ":" + portoDBPrincipal);
        try (Socket s = new Socket(ipPrincipal, portoDBPrincipal);
             InputStream is = s.getInputStream();
             FileOutputStream fos = new FileOutputStream(dbPath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            System.out.println("[Backup] Cópia da BD recebida e salva.");

        } catch (Exception e) {
            System.err.println("[Backup] ERRO: Falha ao obter cópia inicial da BD. A terminar.");
            System.exit(1);
        }
    }

    // --- COMUNICAÇÃO COM DIRETORIA (UDP/Registo) ---
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

    // --- IMPLEMENTAÇÃO DA INTERFACE ServerAPI ---

    // Usado pelo ClientHandler após uma escrita (INSERT/UPDATE) na BD.
    @Override
    public void publicarAlteracao(String querySQL, int novaVersao) {
        if (!this.isPrincipal) {
            // Um Servidor Backup não pode alterar a BD nem publicar Heartbeats de escrita.
            System.err.println("[Heartbeat] ERRO: Servidor Backup tentou publicar alteração.");
            return;
        }

        // Disparar o Heartbeat de escrita imediatamente
        if (heartbeatSender != null) {
            heartbeatSender.dispararHeartbeatEscrita(querySQL, novaVersao);
        }
    }

    // Usado pelo ClientHandler para notificar outros clientes de eventos.
    @Override
    public void notificarTodosClientes(String mensagem) {
        // Envia a notificação para todos os clientes ativos.
        clientesConectados.removeIf(handler -> {
            // Assume-se que o ClientHandler tem o método getUserEmail() e enviarNotificacao(String)
            if (handler.enviarNotificacao(mensagem)) {
                return false; // Manter na lista
            } else {
                System.out.println("[Notificacao] Cliente desconectado durante notificação.");
                return true; // Remover da lista
            }
        });
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java Servidor <ip_dir> <porto_dir> <bd_file> <multicast_ip_interface>");
            return;
        }
        new Servidor(args[0], Integer.parseInt(args[1]), args[2], args[3]).iniciar();
    }
}