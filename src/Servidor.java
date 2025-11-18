import java.io.*;
import java.net.*;


public class Servidor {

    // Argumentos da linha de comando
    private final String ipDiretorio;
    private final int portoDiretorio;
    private final String dbPath; // Ainda não vamos usar, mas guardamos
    private final String ipMulticast; // Ainda não vamos usar, mas guardamos

    // Sockets TCP que o servidor vai usar
    private ServerSocket srvSocketClientes;
    private ServerSocket srvSocketDB;

    // Portos TCP automáticos que foram alocados
    private int portoClienteTCP;
    private int portoBDT_TCP;

    public Servidor(String ipDir, int pDir, String dbPath, String ipMulti) {
        this.ipDiretorio = ipDir;
        this.portoDiretorio = pDir;
        this.dbPath = dbPath;
        this.ipMulticast = ipMulti;

        System.out.println("[Servidor] A arrancar...");
    }

    /**
     * O método principal que inicia o servidor.
     */
    public void iniciar() {

        // 1. Abrir os ServerSockets TCP em portos automáticos (porto 0)
        //    Isto é o que o TP quer dizer com "portos TCP automáticos" 
        try (ServerSocket srvClientes = new ServerSocket(0);
             ServerSocket srvDB = new ServerSocket(0)) {

            this.srvSocketClientes = srvClientes;
            this.srvSocketDB = srvDB;

            // Guarda os portos que o SO nos deu
            this.portoClienteTCP = srvClientes.getLocalPort();
            this.portoBDT_TCP = srvDB.getLocalPort();

            System.out.println("[Servidor] A escutar Clientes em TCP: " + portoClienteTCP);
            System.out.println("[Servidor] A escutar outros Servidores (BD) em TCP: " + portoBDT_TCP);

        } catch (IOException e) {
            System.err.println("[Servidor] Erro fatal ao abrir portos TCP: " + e.getMessage());
            return; // Termina se não conseguir abrir os portos
        }

        // 2. Registar no Serviço de Diretoria
        System.out.println("[Servidor] A contactar Diretoria em " + ipDiretorio + ":" + portoDiretorio);
        MsgRespostaDiretoria resposta = registarNoDiretorio();

        // 3. Validar a resposta
        if (resposta == null) {
            System.err.println("[Servidor] Não foi possível registar no Diretoria. A terminar." [cite: 5453]);
            return;
        }

        if (!resposta.existeServidor()) {
            System.err.println("[Servidor] Diretoria respondeu, mas não há servidor principal (??). A terminar.");
            return;
        }

        System.out.println("[Servidor] Registado com sucesso. O Servidor Principal é: " +
                resposta.getIpServidorPrincipal().getHostAddress() + ":" +
                resposta.getPortoClienteTCP());

        // 4. Decidir o que fazer: Sou o Principal ou um Backup?
        try {
            if (resposta.getIpServidorPrincipal().equals(InetAddress.getLocalHost()) &&
                    resposta.getPortoClienteTCP() == this.portoClienteTCP) {

                System.out.println("[Servidor] EU SOU O SERVIDOR PRINCIPAL.");
                // TODO: Passo 5.1 - Verificar/Criar BD local [cite: 5454, 5455]
                // (verificarBDLocal());

            } else {
                System.out.println("[Servidor] EU SOU UM SERVIDOR DE BACKUP.");
                // TODO: Passo 5.2 - Obter cópia da BD do Principal [cite: 5456]
                // (obterBDdoPrincipal(resposta.getIpServidorPrincipal(), resposta.getPortoBDT_TCP()));
            }

            // TODO: Passo 6 - Iniciar threads
            // (iniciarHeartbeats());
            // (ouvirMulticast());
            // (aceitarClientes());
            // (aceitarPedidosBD());

            System.out.println("[Servidor] ...Servidor pronto a operar (lógica principal ainda por implementar)...");

        } catch (UnknownHostException e) {
            System.err.println("[Servidor] Erro a verificar o meu próprio IP: " + e.getMessage());
        }
    }

    /**
     * Envia um datagrama UDP de registo e espera pela resposta.
     * @return A Resposta do Diretoria, ou null em caso de falha.
     */
    private MsgRespostaDiretoria registarNoDiretorio() {

        // try-with-resources para o DatagramSocket fechar sozinho
        try (DatagramSocket socket = new DatagramSocket()) {

            // Define um timeout (ex: 5 segundos) para a resposta 
            socket.setSoTimeout(5000);

            // 1. Criar a mensagem de registo
            MsgRegistoServidor msgEnvio = new MsgRegistoServidor(portoClienteTCP, portoBDT_TCP);

            // 2. Serializar a mensagem para um array de bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(msgEnvio);
            byte[] bufferEnvio = baos.toByteArray();

            // 3. Enviar o pacote
            InetAddress ipDir = InetAddress.getByName(this.ipDiretorio);
            DatagramPacket packetEnvio = new DatagramPacket(bufferEnvio, bufferEnvio.length, ipDir, this.portoDiretorio);
            socket.send(packetEnvio);

            // 4. Esperar pela resposta
            byte[] bufferRececao = new byte[65535];
            DatagramPacket packetRececao = new DatagramPacket(bufferRececao, bufferRececao.length);

            socket.receive(packetRececao); // Bloqueia até receber ou dar timeout

            // 5. Deserializar a resposta
            ByteArrayInputStream bais = new ByteArrayInputStream(packetRececao.getData());
            ObjectInputStream ois = new ObjectInputStream(bais);

            return (MsgRespostaDiretoria) ois.readObject();

        } catch (SocketTimeoutException e) {
            System.err.println("[Servidor] Erro: O Serviço de Diretoria não respondeu (timeout).");
            return null; // Cumpre o requisito 
        } catch (Exception e) {
            System.err.println("[Servidor] Erro ao comunicar com o Diretoria: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    //verifica se server principal tem bd local, se nao tiver cria nova bd versao 0
    private void verificarBDLocal() {
        File dbFile = new File(this.dbPath);

        if (!dbFile.exists()) {
            System.out.println("[Principal] Ficheiro de BD não encontrado. A criar nova BD (Versão 0)...");
        } else {
            System.out.println("[Principal] BD local encontrada. A usar: " + this.dbPath);
        }
    }

    //server backup obtém bd do server principal, atraves de tcp
    private boolean obterBDdoPrincipal(InetAddress ipPrincipal, int portoDB_Principal) {
        System.out.println("[Backup] A obter cópia da BD de " + ipPrincipal.getHostAddress() + ":" + portoDB_Principal);

        try (Socket socket = new Socket(ipPrincipal, portoDB_Principal);
             InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(this.dbPath)) {


            byte[] buffer = new byte[8192];
            int bytesRead;

            System.out.println("[Backup] Recebendo ficheiro da BD...");
            while ((bytesRead = is.read(buffer)) > 0) {
                fos.write(buffer, 0, bytesRead);
            }

            System.out.println("[Backup] Transferência da BD concluída com sucesso.");
            return true;

        } catch (IOException e) {
            System.err.println("[Backup] Erro ao obter BD do Principal. A terminar execução: " + e.getMessage());
            return false;
        }
    }

    //thread do server principal que aceita pedidos de copia da bd
    private void aceitarPedidosBD() {
        new Thread(() -> {
            try {
                while (true) {
                    System.out.println("[AceitarBD] À espera de pedidos de cópia da BD...");
                    Socket socket = srvSocketDB.accept();
                    System.out.println("[AceitarBD] Pedido de cópia da BD recebido.");

                    new Thread(() -> enviarBD(socket)).start();
                }
            } catch (IOException e) {
                System.err.println("[AceitarBD] Erro na thread de escuta da BD: " + e.getMessage());
            }
        }).start();
    }

    //envia o ficheiro da bd para o server backup
    private void enviarBD(Socket socket) {
        try (OutputStream os = socket.getOutputStream();
             FileInputStream fis = new FileInputStream(this.dbPath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) > 0) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            System.out.println("[AceitarBD] Ficheiro BD enviado com sucesso.");

        } catch (IOException e) {
            System.err.println("[AceitarBD] Erro ao enviar ficheiro BD: " + e.getMessage());
        }
    }

    /**
     * Método Main para arrancar o Servidor.
     * Lê os 4 argumentos da linha de comando.
     */
    public static void main(String[] args) {
        // O TP exige 4 argumentos na linha de comando [cite: 5420, 5422, 5423, 5424]
        if (args.length != 4) {
            System.out.println("Sintaxe: java Servidor <ip_diretoria> <porto_udp_diretoria> <path_db> <ip_multicast>");
            return;
        }

        try {
            String ipDir = args[0];
            int portoDir = Integer.parseInt(args[1]);
            String dbPath = args[2];
            String ipMulti = args[3];

            Servidor servidor = new Servidor(ipDir, portoDir, dbPath, ipMulti);
            servidor.iniciar();

        } catch (NumberFormatException e) {
            System.out.println("O <porto_udp_diretoria> tem de ser um número.");
        }
    }
}