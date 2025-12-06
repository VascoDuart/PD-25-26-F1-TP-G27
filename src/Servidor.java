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
    private MulticastListener multicastListener; // Variável para controlar a thread Multicast

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
                this.isPrincipal = (resposta.getPortoClienteTCP() == this.portoClienteTCP);

                // 5. Iniciar Threads - ORDEM CRÍTICA

                // Thread 1: Heartbeat (inicia o papel inicial - Principal ou Backup)
                this.heartbeatSender = new HeartbeatSender(this, db, ipDiretorio, portoDiretorio, portoClienteTCP, portoBDT_TCP, ipLocal);
                this.heartbeatSender.updateRole(this.isPrincipal); // Determina o papel inicial
                new Thread(this.heartbeatSender).start();

                // Thread 2: Multicast Listener (todos escutam)
                this.multicastListener = new MulticastListener(this, db, ipMulticast, ipLocal);
                new Thread(this.multicastListener).start();

                // Thread 3 & 4: Aceitação de Conexões TCP (devem estar sempre a correr)
                aceitarPedidosBD();
                aceitarClientes();

                // 6. LOGS
                if (this.isPrincipal) {
                    System.out.println("[Servidor] >>> MODO PRINCIPAL <<<");
                } else {
                    System.out.println("[Servidor] >>> MODO BACKUP <<<");
                    // 7. Se for Backup, recebe cópia da BD e fica em espera.
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

    public boolean isPrincipal() {
        return this.isPrincipal;
    }

    public InetAddress getIPLocal() {
        return this.ipLocal;
    }

    public int getPortoClienteTCP() {
        return this.portoClienteTCP;
    }

    /**
     * Rotina crítica para promover o servidor Backup a Principal (Handover).
     * Chamada pelo HeartbeatSender quando a Diretoria confirma a promoção.
     */
    public synchronized void ativarServicosPrincipal() {
        if (this.isPrincipal) return;

        this.isPrincipal = true;
        System.out.println("\n\n************************************************");
        System.out.println("[Servidor] >>> PROMOVIDO A PRINCIPAL <<< ");
        System.out.println("************************************************\n");

        // 1. OBRIGATÓRIO: PARAR A THREAD DE ESCUTA MULTICAST (REPLICAÇÃO DE BACKUP)
        // Isto impede que o novo Principal se torne instável ao receber updates antigos.
        if (this.multicastListener != null) {
            this.multicastListener.stop();
            this.multicastListener = null;
            System.out.println("[Servidor] Multicast Listener (Replicação) parado.");
        }

        // 2. ATUALIZAR HEARTBEAT SENDER
        // O HeartbeatSender deve agora enviar Heartbeats como Principal.
        if (this.heartbeatSender != null) {
            this.heartbeatSender.updateRole(true);
            System.out.println("[Servidor] Heartbeat Sender atualizado para Principal.");
        }

        // 3. ATIVAÇÃO DE SERVIÇOS TCP: O flag 'isPrincipal = true' desbloqueia os ClientHandlers.
        notificarTodosClientes("Serviço Principal restabelecido.");
    }

    private void fecharRecursos() {
        if (heartbeatSender != null) {
            heartbeatSender.stop(); // Interrompe a thread Heartbeat
        }
        if (multicastListener != null) {
            multicastListener.stop(); // Encerra a escuta Multicast
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
    @Override
    public void publicarAlteracao(String querySQL, int novaVersao) {
        if (!this.isPrincipal) {
            System.err.println("[Heartbeat] ERRO: Servidor Backup tentou publicar alteração.");
            return;
        }

        if (heartbeatSender != null) {
            heartbeatSender.dispararHeartbeatEscrita(querySQL, novaVersao);
        }
    }

    @Override
    public void notificarTodosClientes(String mensagem) {
        clientesConectados.removeIf(handler -> {
            if (handler.enviarNotificacao(mensagem)) {
                return false;
            } else {
                System.out.println("[Notificacao] Cliente desconectado durante notificação.");
                return true;
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