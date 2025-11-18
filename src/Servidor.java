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
            System.out.println("[Servidor] Escuta DB TCP: " + portoBDT_TCP);

            // --- 1. Registar no Diretoria ---
            System.out.println("[Servidor] A contactar Diretoria " + ipDiretorio + ":" + portoDiretorio);
            MsgRespostaDiretoria resposta = registarNoDiretorio();

            if (resposta == null || !resposta.existeServidor()) {
                System.err.println("[Servidor] Falha no registo ou resposta inválida. A sair.");
                return;
            }

            System.out.println("[Servidor] O Diretoria diz que o Principal é: " +
                    resposta.getIpServidorPrincipal() + ":" + resposta.getPortoClienteTCP());

            // --- 2. Verificar Identidade (CORREÇÃO AQUI) ---
            // Comparamos o porto TCP de clientes. Se for igual ao meu, sou eu o Principal.
            boolean souEu = (resposta.getPortoClienteTCP() == this.portoClienteTCP);

            if (souEu) {
                System.out.println("[Servidor] >>> EU SOU O PRINCIPAL <<<");
                verificarBDLocal();

                // IMPORTANTE: Ativa a thread que deixa os backups copiarem a BD
                aceitarPedidosBD();

            } else {
                System.out.println("[Servidor] >>> EU SOU BACKUP <<<");
                obterBDdoPrincipal(resposta.getIpServidorPrincipal(), resposta.getPortoBDT_TCP());
            }

            // --- 3. Loop Principal ---
            System.out.println("[Servidor] Em funcionamento...");
            while(true) { Thread.sleep(10000); }

        } catch (Exception e) {
            e.printStackTrace();
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
            System.out.println("[Servidor] Erro no registo: " + e.getMessage());
            return null;
        }
    }

    // Simulação: Não tenta ler ficheiros reais para não dar erro
    private void verificarBDLocal() {
        System.out.println("[Principal] (Simulação) BD verificada em " + this.dbPath);
    }

    // Simulação: Conecta-se mas não guarda ficheiro
    private boolean obterBDdoPrincipal(InetAddress ipPrincipal, int portoDB) {
        System.out.println("[Backup] A pedir BD a " + ipPrincipal + ":" + portoDB);
        try (Socket s = new Socket(ipPrincipal, portoDB);
             InputStream is = s.getInputStream()) {

            // Lê o que o principal mandar só para testar a rede
            byte[] b = new byte[1024];
            int lidos = is.read(b);
            System.out.println("[Backup] Recebi " + lidos + " bytes de 'BD'. Sucesso!");
            return true;
        } catch (IOException e) {
            System.out.println("[Backup] Erro a obter BD: " + e.getMessage());
            return false;
        }
    }

    private void aceitarPedidosBD() {
        new Thread(() -> {
            try {
                while (true) {
                    // Fica bloqueado aqui à espera de conexões
                    Socket s = srvSocketDB.accept();
                    System.out.println("[AceitarBD] Backup conectou-se. A enviar dados...");
                    new Thread(() -> enviarBD(s)).start();
                }
            } catch (IOException e) {
                System.err.println("[AceitarBD] Erro no socket: " + e.getMessage());
            }
        }).start();
    }






    private void enviarBD(Socket s) {
        try (OutputStream os = s.getOutputStream()) {
            // Envia dados falsos só para testar
            System.out.println("[AceitarBD] A enviar bytes...");
            os.write("DADOS_DA_BD_FALSA".getBytes());
            os.flush();
            System.out.println("[AceitarBD] Envio concluído.");
        } catch (IOException e) {
            System.err.println("[AceitarBD] Erro a enviar: " + e.getMessage());
        } finally {
            try { s.close(); } catch (IOException e) {}
        }
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java Servidor <ip_dir> <porto_dir> <bd> <multicast>");
            return;
        }
        new Servidor(args[0], Integer.parseInt(args[1]), args[2], args[3]).iniciar();
    }
}