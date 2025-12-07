package pt.isec.pd.tp.servidor;

import pt.isec.pd.tp.DatabaseManager;
import pt.isec.pd.tp.HeartbeatSender;
import pt.isec.pd.tp.MulticastListener;
import pt.isec.pd.tp.cliente.ClientHandler;
import pt.isec.pd.tp.mensagens.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
    private MulticastListener multicastListener;

    private final List<ClientHandler> clientesConectados = Collections.synchronizedList(new ArrayList<>());
    private final Object BD_LOCK = new Object();

    private boolean isPrincipal = false;

    public Servidor(String ipDir, int pDir, String dbPath, String ipMulti) {
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.dbPath = dbPath;
        this.ipMulticast = ipMulti;
        System.out.println("[pt.isec.pd.tp.servidor.Servidor] A configurar...");
    }

    public void iniciar() {
        try {

            this.ipLocal = InetAddress.getLocalHost();

            try (ServerSocket srvClientes = new ServerSocket(0);
                 ServerSocket srvDB = new ServerSocket(0)) {

                this.srvSocketClientes = srvClientes;
                this.srvSocketDB = srvDB;

                this.portoClienteTCP = srvClientes.getLocalPort();
                this.portoBDT_TCP = srvDB.getLocalPort();

                System.out.println("[pt.isec.pd.tp.servidor.Servidor] A escutar Clientes TCP no porto: " + portoClienteTCP);


                prepararBaseDeDados();


                MsgRespostaDiretoria resposta = registarNoDiretorio();

                if (resposta == null || !resposta.existeServidor()) {
                    System.err.println("[pt.isec.pd.tp.servidor.Servidor] Falha no registo na Diretoria. A terminar.");
                    fecharRecursos();
                    return;
                }


                this.isPrincipal = (resposta.getPortoClienteTCP() == this.portoClienteTCP);




                this.heartbeatSender = new HeartbeatSender(this, db, ipDiretorio, portoDiretorio, portoClienteTCP, portoBDT_TCP, ipLocal);
                this.heartbeatSender.updateRole(this.isPrincipal);
                new Thread(this.heartbeatSender).start();


                this.multicastListener = new MulticastListener(this, db, ipMulticast, ipLocal);
                new Thread(this.multicastListener).start();


                aceitarPedidosBD();
                aceitarClientes();


                if (this.isPrincipal) {
                    System.out.println("[pt.isec.pd.tp.servidor.Servidor] >>> MODO PRINCIPAL <<<");
                } else {
                    System.out.println("[pt.isec.pd.tp.servidor.Servidor] >>> MODO BACKUP <<<");

                    receberCopiaBD(resposta.getIpServidorPrincipal(), resposta.getPortoBDT_TCP());
                }


                System.out.println("[pt.isec.pd.tp.servidor.Servidor] Em funcionamento. Pressione Ctrl+C para sair.");
                while(true) { Thread.sleep(10000); }

            }
        } catch (InterruptedException e) {
            System.out.println("[pt.isec.pd.tp.servidor.Servidor] Thread principal interrompida.");
        } catch (Exception e) {
            System.err.println("[pt.isec.pd.tp.servidor.Servidor] Erro fatal durante a inicialização: " + e.getMessage());
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


    public synchronized void ativarServicosPrincipal() {
        if (this.isPrincipal) return;

        this.isPrincipal = true;
        System.out.println("\n\n************************************************");
        System.out.println("[pt.isec.pd.tp.servidor.Servidor] >>> PROMOVIDO A PRINCIPAL <<< ");
        System.out.println("************************************************\n");


        if (this.multicastListener != null) {
            this.multicastListener.stop();
            this.multicastListener = null;
            System.out.println("[pt.isec.pd.tp.servidor.Servidor] Multicast Listener (Replicação) parado.");
        }


        if (this.heartbeatSender != null) {
            this.heartbeatSender.updateRole(true);
            System.out.println("[pt.isec.pd.tp.servidor.Servidor] Heartbeat Sender atualizado para Principal.");
        }


        notificarTodosClientes("Serviço Principal restabelecido.");
    }

    private void fecharRecursos() {
        if (heartbeatSender != null) {
            heartbeatSender.stop();
        }
        if (multicastListener != null) {
            multicastListener.stop();
        }
        try { srvSocketClientes.close(); } catch (Exception e) {}
        try { srvSocketDB.close(); } catch (Exception e) {}
        if (db != null) db.desconectar();

    }

    private void prepararBaseDeDados() throws Exception {
        this.db = new DatabaseManager(this.dbPath);
        db.conectar();
        db.criarTabelas();
        System.out.println("[Principal] BD pronta e tabelas verificadas.");
    }


    private void aceitarClientes() {
        new Thread(() -> {
            System.out.println("[Accept] Thread de aceitação de clientes iniciada.");
            while (true) {
                try {
                    Socket s = srvSocketClientes.accept();
                    try {

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


    @Override
    public void publicarAlteracao(String querySQL, int novaVersao) {
        if (!this.isPrincipal) {
            System.err.println("[Heartbeat] ERRO: pt.isec.pd.tp.servidor.Servidor Backup tentou publicar alteração.");
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
                System.out.println("[Notificacao] pt.isec.pd.tp.cliente.Cliente desconectado durante notificação.");
                return true;
            }
        });
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java pt.isec.pd.tp.servidor.Servidor <ip_dir> <porto_dir> <bd_file> <multicast_ip_interface>");
            return;
        }
        new Servidor(args[0], Integer.parseInt(args[1]), args[2], args[3]).iniciar();
    }
}