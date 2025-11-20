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

    // Variável para gerir a BD em todo o servidor
    private DatabaseManager db;

    public Servidor(String ipDir, int pDir, String dbPath, String ipMulti) {
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.dbPath = dbPath;
        this.ipMulticast = ipMulti;
        System.out.println("[Servidor] A arrancar...");
    }

    public void iniciar() {
        try (ServerSocket srvClientes = new ServerSocket(0);
             ServerSocket srvDB = new ServerSocket(0)) {

            this.srvSocketClientes = srvClientes;
            this.srvSocketDB = srvDB;

            this.portoClienteTCP = srvClientes.getLocalPort();
            this.portoBDT_TCP = srvDB.getLocalPort();

            System.out.println("[Servidor] Escuta Clientes TCP: " + portoClienteTCP);

            // 1. Registar no Diretoria
            MsgRespostaDiretoria resposta = registarNoDiretorio();

            if (resposta == null || !resposta.existeServidor()) {
                System.err.println("[Servidor] Falha no registo. A sair.");
                return;
            }

            // 2. Verificar Identidade
            boolean souEu = (resposta.getPortoClienteTCP() == this.portoClienteTCP);

            if (souEu) {
                System.out.println("[Servidor] >>> EU SOU O PRINCIPAL <<<");

                // Prepara a BD e mantém a ligação aberta
                prepararBaseDeDados();

                aceitarPedidosBD();
                aceitarClientes(); // Começa a aceitar registos!

            } else {
                System.out.println("[Servidor] >>> EU SOU BACKUP <<<");
                obterBDdoPrincipal(resposta.getIpServidorPrincipal(), resposta.getPortoBDT_TCP());
            }

            // Loop para manter o servidor vivo
            System.out.println("[Servidor] Em funcionamento...");
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
            // Nota: NÃO desconectamos aqui, para podermos usar a 'db' nos clientes
            System.out.println("[Principal] BD pronta.");
        } catch (Exception e) {
            System.err.println("[Principal] Erro fatal na BD: " + e.getMessage());
            System.exit(1);
        }
    }

    private void aceitarClientes() {
        new Thread(() -> {
            try {
                while (true) {
                    Socket s = srvSocketClientes.accept();
                    // Cria uma thread para atender este cliente específico
                    new Thread(() -> atenderCliente(s)).start();
                }
            } catch (IOException e) {
                System.err.println("[Clientes] Erro no socket: " + e.getMessage());
            }
        }).start();
    }

    private void atenderCliente(Socket s) {
        try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

            // 1. Ler mensagem do cliente
            Object msg = in.readObject();

            if (msg instanceof MsgRegisto) {
                MsgRegisto pedido = (MsgRegisto) msg;
                boolean sucesso = false;

                // --- VERIFICAÇÃO CRÍTICA AQUI ---
                if (pedido.isDocente()) {
                    Docente d = pedido.getDocente();
                    System.out.println("[Cliente] Recebido pedido de registo DOCENTE para: " + d.getEmail());
                    // TODO: Adicionar validação do código único do docente
                    sucesso = db.registarDocente(d);

                } else if (pedido.isEstudante()) {
                    Estudante e = pedido.getEstudante();
                    System.out.println("[Cliente] Recebido pedido de registo ESTUDANTE para: " + e.getEmail());
                    // TODO: Criar a função db.registarEstudante(e)
                    // sucesso = db.registarEstudante(e);

                } else {
                    // Caso a mensagem não tenha nem Docente nem Estudante
                    out.writeObject("ERRO: Mensagem de registo inválida.");
                    out.flush();
                    return;
                }

                // 2. Responder ao cliente
                if (sucesso) {
                    out.writeObject("SUCESSO: Utilizador registado!");
                } else {
                    out.writeObject("ERRO: Email/Número já existe ou dados inválidos.");
                }
                out.flush();
            }
            // TODO: Adicionar processamento para MsgLogin
            // ...

        } catch (Exception e) {
            System.err.println("[Cliente] Erro na ligação com cliente: " + e.getMessage());
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
        } catch (Exception e) { return null; }
    }

    // Backup pede BD ao Principal
    private boolean obterBDdoPrincipal(InetAddress ipPrincipal, int portoDB) {
        System.out.println("[Backup] A pedir BD a " + ipPrincipal + ":" + portoDB);
        try (Socket s = new Socket(ipPrincipal, portoDB);
             InputStream is = s.getInputStream()) {
            byte[] b = new byte[1024];
            int lidos = is.read(b);
            System.out.println("[Backup] Recebi " + lidos + " bytes de 'BD'. Sucesso!");
            return true;
        } catch (IOException e) {
            System.out.println("[Backup] Erro a obter BD: " + e.getMessage());
            return false;
        }
    }

    // Principal envia BD ao Backup
    private void aceitarPedidosBD() {
        new Thread(() -> {
            try {
                while (true) {
                    Socket s = srvSocketDB.accept();
                    System.out.println("[AceitarBD] Backup conectou-se. A enviar dados...");
                    new Thread(() -> enviarBD(s)).start();
                }
            } catch (IOException e) {}
        }).start();
    }

    private void enviarBD(Socket s) {
        try (OutputStream os = s.getOutputStream()) {
            // Envia dados falsos só para testar por enquanto
            os.write("DADOS_DA_BD_FALSA".getBytes());
            os.flush();
        } catch (IOException e) {}
        finally { try { s.close(); } catch (Exception e) {} }
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java Servidor <ip_dir> <porto_dir> <bd> <multicast>");
            return;
        }
        new Servidor(args[0], Integer.parseInt(args[1]), args[2], args[3]).iniciar();
    }
}