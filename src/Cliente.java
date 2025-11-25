import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Cliente {

    private static final int ESTADO_INICIAL = 0;
    private static final int ESTADO_DOCENTE = 1;
    private static final int ESTADO_ESTUDANTE = 2;

    private static int estadoLogin = ESTADO_INICIAL;
    private static Scanner scanner = new Scanner(System.in);

    private static InetAddress ipPrincipal;
    private static int portoClienteTCP;

    // Variáveis para streams (úteis para passar entre métodos)
    private static ObjectOutputStream out;
    private static ObjectInputStream in;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Sintaxe: java Cliente <ip_diretoria> <porto_diretoria>");
            return;
        }

        String ipDir = args[0];
        int portoDir = Integer.parseInt(args[1]);

        InetAddress ultimoIP = null;
        int ultimoPorto = -1;

        while (true) {
            // 1. Encontrar o Servidor
            if (!encontrarServidor(ipDir, portoDir, ultimoIP, ultimoPorto)) {
                System.out.println("A tentar novamente em 5 segundos...");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                continue;
            }

            // Atualiza o "último servidor conhecido"
            ultimoIP = ipPrincipal;
            ultimoPorto = portoClienteTCP;

            // 2. Iniciar Sessão (Retorna TRUE se foi Logout voluntário, FALSE se foi erro)
            boolean logoutLimpo = iniciarSessao();

            if (logoutLimpo) {
                // SE FOI LOGOUT, NÃO ESPERAMOS 20 SEGUNDOS!
                // Esquecemos o último servidor para conectar imediatamente ao próximo (ou mesmo)
                ultimoIP = null;
                ultimoPorto = -1;
                estadoLogin = ESTADO_INICIAL;
            }
        }
    }

    private static boolean encontrarServidor(String ipDir, int portoDir, InetAddress ultimoIP, int ultimoPorto) {
        System.out.println("\n[Cliente] A procurar servidor principal...");

        try (DatagramSocket udpSocket = new DatagramSocket()) {
            udpSocket.setSoTimeout(5000);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(new MsgPedidoServidor());
            byte[] data = baos.toByteArray();

            udpSocket.send(new DatagramPacket(data, data.length, InetAddress.getByName(ipDir), portoDir));

            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);

            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
            MsgRespostaDiretoria resposta = (MsgRespostaDiretoria) ois.readObject();

            if (!resposta.existeServidor()) {
                System.out.println("[Cliente] Nenhum servidor disponível.");
                return false;
            }

            ipPrincipal = resposta.getIpServidorPrincipal();
            portoClienteTCP = resposta.getPortoClienteTCP();
            System.out.println("[Cliente] Servidor encontrado em: " + ipPrincipal.getHostAddress() + ":" + portoClienteTCP);

            // Regra dos 20 segundos (SÓ SE APLICA SE O SERVIDOR FOR O MESMO DO CRASH ANTERIOR)
            if (ultimoIP != null && ultimoPorto != -1 &&
                    ipPrincipal.equals(ultimoIP) && portoClienteTCP == ultimoPorto) {

                System.out.println("[Cliente] O Servidor principal é o mesmo que falhou. A tentar novamente em 20 segundos...");
                try { Thread.sleep(20000); } catch (InterruptedException ignored) {}
                // Tenta recursivamente
                return encontrarServidor(ipDir, portoDir, null, -1);
            }

            return true;

        } catch (Exception e) {
            System.err.println("[Cliente] Erro ao contactar Diretoria: " + e.getMessage());
            return false;
        }
    }

    // Retorna true se o utilizador escolheu sair/logout. Retorna false se a ligação caiu.
    private static boolean iniciarSessao() {
        try (Socket socket = new Socket(ipPrincipal, portoClienteTCP)) {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            System.out.println("[Cliente] Ligação TCP estabelecida.");

            while (socket.isConnected()) {
                if (estadoLogin == ESTADO_INICIAL) {
                    // Se retornar false, quer dizer que escolheu "Sair" da app
                    if (!mostrarMenuInicial()) return true;
                } else if (estadoLogin == ESTADO_DOCENTE) {
                    // Se retornar false, quer dizer que escolheu "Logout"
                    if (!menuDocente()) return true;
                } else if (estadoLogin == ESTADO_ESTUDANTE) {
                    // Se retornar false, quer dizer que escolheu "Logout"
                    if (!menuEstudante()) return true;
                }
            }
            return true;

        } catch (Exception e) {
            System.err.println("[Cliente] Ligação perdida: " + e.getMessage());
            return false; // Foi um crash, ativa a regra dos 20s no main
        }
    }

    // Retorna false para Sair da App
    private static boolean mostrarMenuInicial() throws Exception {
        System.out.println("\n--- Menu Principal ---");
        System.out.println("1. Autenticar");
        System.out.println("2. Registar Docente");
        System.out.println("3. Registar Estudante");
        System.out.println("0. Sair");
        System.out.print("Opção: ");

        String op = scanner.nextLine();
        switch (op) {
            case "1": processarLogin(); break;
            case "2": processarRegistoDocente(); break;
            case "3": processarRegistoEstudante(); break;
            case "0": return false; // Sair
            default: System.out.println("Opção inválida.");
        }
        return true;
    }

    private static void processarLogin() throws IOException, ClassNotFoundException {
        System.out.print("Email: "); String email = scanner.nextLine();
        System.out.print("Password: "); String pass = scanner.nextLine();

        out.writeObject(new MsgLogin(email, pass));
        out.flush();

        Object resposta = in.readObject();
        String respStr = (String) resposta;
        System.out.println("[Servidor]: " + respStr);

        if (respStr.contains("sucesso Docente")) estadoLogin = ESTADO_DOCENTE;
        else if (respStr.contains("sucesso Estudante")) estadoLogin = ESTADO_ESTUDANTE;
    }

    private static void processarRegistoDocente() throws IOException, ClassNotFoundException {
        System.out.print("Nome: "); String nome = scanner.nextLine();
        System.out.print("Email: "); String email = scanner.nextLine();
        System.out.print("Password: "); String pass = scanner.nextLine();
        System.out.print("Código Único: "); String token = scanner.nextLine();

        out.writeObject(new MsgRegisto(new Docente(nome, email, pass), token));
        out.flush();
        System.out.println("[Servidor]: " + in.readObject());
    }

    private static void processarRegistoEstudante() throws IOException, ClassNotFoundException {
        System.out.print("Nome: "); String nome = scanner.nextLine();
        System.out.print("Email: "); String email = scanner.nextLine();
        System.out.print("Password: "); String pass = scanner.nextLine();
        System.out.print("Número Estudante: "); String num = scanner.nextLine();

        out.writeObject(new MsgRegisto(new Estudante(num, nome, email, pass)));
        out.flush();
        System.out.println("[Servidor]: " + in.readObject());
    }

    // Retorna false para fazer Logout
    private static boolean menuDocente() throws Exception {
        System.out.println("\n--- DOCENTE ---");
        System.out.println("1. Criar Pergunta");
        System.out.println("0. Logout");
        System.out.print("> ");

        String op = scanner.nextLine();
        if (op.equals("1")) {
            criarPergunta();
        } else if (op.equals("0")) {
            estadoLogin = ESTADO_INICIAL;
            System.out.println("Logout efetuado.");
            return false; // Volta ao loop de iniciarSessao que retorna true
        }
        return true;
    }

    private static void criarPergunta() throws Exception {
        System.out.println("\n--- Nova Pergunta ---");
        System.out.print("Enunciado: "); String enunciado = scanner.nextLine();
        System.out.print("Início (YYYY-MM-DD HH:mm:ss): "); String inicio = scanner.nextLine();
        System.out.print("Fim (YYYY-MM-DD HH:mm:ss): "); String fim = scanner.nextLine();

        List<Opcao> opcoes = new ArrayList<>();
        char letra = 'a';
        System.out.println("Inserir opções (Enter vazio para terminar):");
        while (true) {
            System.out.print(letra + ") ");
            String texto = scanner.nextLine();

            if (texto.isEmpty()) {
                if (opcoes.size() < 2) {
                    System.out.println("Erro: Mínimo 2 opções.");
                    continue;
                }
                break;
            }

            System.out.print("Correta? (s/n): ");
            boolean correta = scanner.nextLine().equalsIgnoreCase("s");
            opcoes.add(new Opcao(String.valueOf(letra++), texto, correta));
        }

        // ID -1 pois o servidor sabe quem é pelo login
        out.writeObject(new MsgCriarPergunta(-1, enunciado, inicio, fim, opcoes));
        out.flush();
        System.out.println("[Servidor]: " + in.readObject());
    }

    // Retorna false para fazer Logout


    private static boolean menuEstudante() throws Exception {
        System.out.println("\n--- ESTUDANTE ---");
        System.out.println("1. Responder a Pergunta");
        System.out.println("0. Logout");
        System.out.print("> ");

        String op = scanner.nextLine();
        if (op.equals("1")) {
            responderPergunta();
        } else if (op.equals("0")) {
            estadoLogin = ESTADO_INICIAL;
            return false; // Logout
        }
        return true;
    }

    private static void responderPergunta() throws Exception {
        System.out.print("Introduza o Código de Acesso: ");
        String codigo = scanner.nextLine();

        // 1. Pedir Pergunta
        out.writeObject(new MsgObterPergunta(codigo));
        out.flush();

        Object resposta = in.readObject();
        if (resposta == null) {
            System.out.println("Erro: Pergunta não encontrada ou código inválido.");
            return;
        }

        if (resposta instanceof Pergunta) {
            Pergunta p = (Pergunta) resposta;
            System.out.println("\n--- PERGUNTA ---");
            System.out.println(p.getEnunciado()); // Tens de ter este getter na classe Pergunta
            for (Opcao o : p.getOpcoes()) {       // Tens de ter este getter na classe Pergunta
                System.out.println(o.getLetra() + ") " + o.getTexto());
            }

            System.out.print("A sua resposta (letra): ");
            String letra = scanner.nextLine();

            // 2. Enviar Resposta
            // O ID do aluno (-1) é ignorado porque o handler usa o da sessão, mas a classe pede-o no construtor
            out.writeObject(new MsgResponderPergunta(-1, codigo, letra));
            out.flush();

            System.out.println("[Servidor]: " + in.readObject());
        }
    }
}