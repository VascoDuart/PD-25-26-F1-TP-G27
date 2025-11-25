import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Cliente {

    // Constantes de Estado do Cliente
    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;

    // Variáveis de Estado
    private static int estadoLogin = ESTADO_INICIAL;
    private static Scanner scanner = new Scanner(System.in);

    // Dados do Servidor Principal atual
    private static InetAddress ipPrincipal;
    private static int portoClienteTCP;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Sintaxe: java Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        // Endereço do Servidor anterior (para recuperação de falhas)
        InetAddress ultimoIP = null;
        int ultimoPorto = -1;

        // Loop principal: Tenta encontrar o servidor e manter a sessão
        while (true) {
            // 1. Tentar encontrar o Servidor (UDP)
            if (!encontrarServidor(ipDir, portoDir, ultimoIP, ultimoPorto)) {
                System.out.println("A tentar novamente em 5 segundos...");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                continue;
            }

            // Atualiza os dados do último servidor para recuperação de falhas
            ultimoIP = ipPrincipal;
            ultimoPorto = portoClienteTCP;

            // 2. Interagir com o Servidor Principal (TCP)
            iniciarSessao();

            // Se a sessão acabar (logout/erro), o loop recomeça.
            estadoLogin = ESTADO_INICIAL;
        }
    }

    private static boolean encontrarServidor(String ipDir, int portoDir, InetAddress ultimoIP, int ultimoPorto) {
        System.out.println("\n[Cliente] A procurar servidor principal...");

        try (DatagramSocket udpSocket = new DatagramSocket()) {
            udpSocket.setSoTimeout(5000);

            // ... (Código para enviar MsgPedidoServidor e receber MsgRespostaDiretoria) ...
            MsgPedidoServidor pedido = new MsgPedidoServidor();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(pedido);
            byte[] data = baos.toByteArray();

            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ipDir), portoDir);
            udpSocket.send(packet);

            byte[] buffer = new byte[4096];
            DatagramPacket respPacket = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(respPacket);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(respPacket.getData()));
            MsgRespostaDiretoria resposta = (MsgRespostaDiretoria) ois.readObject();

            if (!resposta.existeServidor()) {
                System.out.println("[Cliente] Nenhum servidor disponível na Diretoria.");
                return false;
            }

            ipPrincipal = resposta.getIpServidorPrincipal();
            portoClienteTCP = resposta.getPortoClienteTCP();
            System.out.println("[Cliente] Servidor encontrado em: " + ipPrincipal.getHostAddress() + ":" + portoClienteTCP);

            if (ultimoIP != null && ultimoPorto != -1 &&
                    ipPrincipal.equals(ultimoIP) && portoClienteTCP == ultimoPorto) {

                System.out.println("[Cliente] O Servidor principal é o mesmo que falhou. A tentar novamente em 20 segundos...");
                try { Thread.sleep(20000); } catch (InterruptedException ignored) {}

                return encontrarServidor(ipDir, portoDir, null, -1);
            }

            return true;

        } catch (SocketTimeoutException e) {
            System.err.println("[Cliente] Timeout: O Serviço de Diretoria não respondeu.");
            return false;
        } catch (Exception e) {
            System.err.println("[Cliente] Erro ao comunicar com a Diretoria: " + e.getMessage());
            return false;
        }
    }

    private static void iniciarSessao() {
        try (Socket socket = new Socket(ipPrincipal, portoClienteTCP);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            System.out.println("[Cliente] Ligação TCP estabelecida com o Servidor Principal.");

            // 1. Fase de Autenticação/Registo
            while (socket.isConnected() && estadoLogin == ESTADO_INICIAL) {
                mostrarMenuInicial(out, in);
            }

            // 2. Fase de Interação (após o login)
            if (estadoLogin == ESTADO_DOCENTE) {
                menuDocente(out, in);
            } else if (estadoLogin == ESTADO_ESTUDANTE) {
                menuEstudante(out, in);
            }

        } catch (ConnectException e) {
            System.err.println("[Cliente] Falha de conexão TCP. O servidor pode ter caído. A solicitar novo servidor...");
        } catch (SocketException | EOFException e) {
            System.err.println("[Cliente] Conexão TCP perdida. O servidor encerrou a ligação. A solicitar novo servidor...");
        } catch (Exception e) {
            System.err.println("[Cliente] Erro inesperado na sessão: " + e.getMessage());
        }
        System.out.println("[Cliente] Sessão encerrada.");
    }

    private static void mostrarMenuInicial(ObjectOutputStream out, ObjectInputStream in) throws Exception {
        System.out.println("\n--- Menu Principal ---");
        System.out.println("1. Autenticar (Docente/Estudante)");
        System.out.println("2. Registar Novo Docente");
        System.out.println("3. Registar Novo Estudante");
        System.out.println("0. Sair");
        System.out.print("Opção: ");

        if (!scanner.hasNextInt()) {
            scanner.next();
            return;
        }
        int opcao = scanner.nextInt();
        scanner.nextLine();

        switch (opcao) {
            case 1:
                processarLogin(out, in);
                break;
            case 2:
                processarRegistoDocente(out, in);
                break;
            case 3:
                processarRegistoEstudante(out, in);
                break;
            case 0:
                System.exit(0);
                break;
            default:
                System.out.println("Opção inválida.");
        }
    }

    private static void processarRegistoDocente(ObjectOutputStream out, ObjectInputStream in) throws Exception {
        System.out.println("\n--- Registo de Docente ---");
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        System.out.print("Código Único de Docente: ");
        String codUnico = scanner.nextLine();

        Docente novoDocente = new Docente(nome, email, password);

        // Utiliza o construtor correto: Docente + Código Único
        MsgRegisto msg = new MsgRegisto(novoDocente, codUnico);
        out.writeObject(msg);
        out.flush();

        String resposta = (String) in.readObject();
        System.out.println("[Servidor Diz]: " + resposta);
    }

    private static void processarRegistoEstudante(ObjectOutputStream out, ObjectInputStream in) throws Exception {
        System.out.println("\n--- Registo de Estudante ---");
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        System.out.print("Número de Estudante: ");
        String numEstudante = scanner.nextLine();

        Estudante novoEstudante = new Estudante(numEstudante, nome, email, password);

        // Utiliza o construtor correto: Estudante
        MsgRegisto msg = new MsgRegisto(novoEstudante);
        out.writeObject(msg);
        out.flush();

        String resposta = (String) in.readObject();
        System.out.println("[Servidor Diz]: " + resposta);
    }

    private static void processarLogin(ObjectOutputStream out, ObjectInputStream in) throws Exception {
        System.out.println("\n--- Autenticação ---");
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        MsgLogin msg = new MsgLogin(email, password);
        out.writeObject(msg);
        out.flush();

        Object resposta = in.readObject();

        if (resposta instanceof String) {
            String respStr = (String) resposta;
            System.out.println("[Servidor Diz]: " + respStr);

            if (respStr.contains("sucesso Docente")) {
                estadoLogin = ESTADO_DOCENTE;
                System.out.println("-> Autenticação de Docente concluída com sucesso.");
            } else if (respStr.contains("sucesso Estudante")) {
                estadoLogin = ESTADO_ESTUDANTE;
                System.out.println("-> Autenticação de Estudante concluída com sucesso.");
            } else {
                System.out.println("-> Falha na autenticação.");
            }
        }
    }

    private static void menuDocente(ObjectOutputStream out, ObjectInputStream in) throws Exception {
        while (true) {
            System.out.println("\n--- MENU DOCENTE ---");
            System.out.println("1. Criar Nova Pergunta");
            System.out.println("2. Listar Minhas Perguntas (Futuro)");
            System.out.println("0. Logout");
            System.out.print("Opção: ");

            String op = scanner.nextLine();

            if (op.equals("1")) {
                criarPergunta(out, in);
            } else if (op.equals("2")) {
                System.out.println("Funcionalidade ainda não implementada.");
            } else if (op.equals("0")) {
                System.out.println("[Docente] A sair...");
                break; // Sai do loop e volta ao estado inicial (Main)
            } else {
                System.out.println("Opção inválida.");
            }
        }
        estadoLogin = ESTADO_INICIAL; // Reset do estado ao sair
    }

    private static void menuEstudante(ObjectOutputStream out, ObjectInputStream in) {
        System.out.println("\n--- Menu Estudante (Logado) ---");
        System.out.println("Funcionalidades pendentes.");
        System.out.println("0. Logout");

        scanner.nextLine();
        estadoLogin = ESTADO_INICIAL;
        System.out.println("[Estudante] Sessão encerrada (Logout).");
    }


    private static void criarPergunta(ObjectOutputStream out, ObjectInputStream in) throws Exception {
        System.out.println("\n--- Nova Pergunta ---");

        System.out.print("Enunciado da pergunta: ");
        String enunciado = scanner.nextLine();

        System.out.print("Data/Hora Início (ex: 2025-12-01 10:00:00): ");
        String inicio = scanner.nextLine();

        System.out.print("Data/Hora Fim (ex: 2025-12-01 12:00:00): ");
        String fim = scanner.nextLine();

        // Recolher Opções
        java.util.List<Opcao> opcoes = new java.util.ArrayList<>();
        System.out.println("Adicione as opções de resposta (Mínimo 2).");
        System.out.println("Deixe o texto vazio para terminar a inserção.");

        char letraAtual = 'a';
        while (true) {
            System.out.print("Opção " + letraAtual + ") Texto: ");
            String texto = scanner.nextLine();

            if (texto.isEmpty()) {
                if (opcoes.size() < 2) {
                    System.out.println("Erro: Tem de inserir pelo menos 2 opções.");
                    continue;
                }
                break;
            }

            System.out.print("Esta é a opção correta? (s/n): ");
            String isCorretaStr = scanner.nextLine();
            boolean isCorreta = isCorretaStr.equalsIgnoreCase("s");

            opcoes.add(new Opcao(String.valueOf(letraAtual), texto, isCorreta));
            letraAtual++;
        }

        // Enviar para o servidor
        // Nota: Passamos -1 no ID porque o Servidor já sabe quem somos pela sessão (ClientHandler)
        MsgCriarPergunta msg = new MsgCriarPergunta(-1, enunciado, inicio, fim, opcoes);
        out.writeObject(msg);
        out.flush();

        // Ler resposta do servidor
        System.out.println("A enviar pergunta...");
        String resposta = (String) in.readObject();
        System.out.println("[Servidor]: " + resposta);
    }
}