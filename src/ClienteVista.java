import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ClienteVista {
    private final Scanner scanner;

    public ClienteVista() {
        this.scanner = new Scanner(System.in);
    }

    // --- MÉTODOS DE SAÍDA ---
    public void mostrarMensagem(String msg) {
        System.out.println("[INFO] " + msg);
    }

    public void mostrarErro(String msg) {
        System.err.println("[ERRO] " + msg);
    }

    public void mostrarNotificacao(String msg) {
        System.out.println("\n>>> NOTIFICAÇÃO: " + msg + " <<<\n> ");
    }

    // --- MÉTODOS DE ENTRADA GENÉRICOS ---
    public String lerTexto(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public int lerInteiro(String prompt) {
        try {
            System.out.print(prompt);
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // --- MENUS ---
    public int menuInicial() {
        System.out.println("\n=== BEM-VINDO ===");
        System.out.println("1. Login");
        System.out.println("2. Registar Docente");
        System.out.println("3. Registar Estudante");
        System.out.println("0. Sair");
        return lerInteiro("Opção: ");
    }

    public int menuDocente() {
        System.out.println("\n=== DOCENTE ===");
        System.out.println("1. Criar Pergunta");
        System.out.println("2. Exportar Resultados (CSV)");
        System.out.println("0. Logout");
        return lerInteiro("Opção: ");
    }

    public int menuEstudante() {
        System.out.println("\n=== ESTUDANTE ===");
        System.out.println("1. Responder a Pergunta");
        System.out.println("0. Logout");
        return lerInteiro("Opção: ");
    }

    // --- FORMULÁRIOS ---
    public MsgLogin formLogin() {
        String email = lerTexto("Email: ");
        String pass = lerTexto("Password: ");
        return new MsgLogin(email, pass);
    }

    public MsgRegisto formRegistoDocente() {
        System.out.println("--- Novo Docente ---");
        String nome = lerTexto("Nome: ");
        String email = lerTexto("Email: ");
        String pass = lerTexto("Password: ");
        String codigo = lerTexto("Código Institucional: ");
        return new MsgRegisto(new Docente(nome, email, pass), codigo);
    }

    public MsgRegisto formRegistoEstudante() {
        System.out.println("--- Novo Estudante ---");
        String nome = lerTexto("Nome: ");
        String email = lerTexto("Email: ");
        String pass = lerTexto("Password: ");
        String num = lerTexto("Nº Estudante: ");
        return new MsgRegisto(new Estudante(num, nome, email, pass));
    }

    public MsgCriarPergunta formCriarPergunta() {
        String enunc = lerTexto("Enunciado: ");
        String ini = lerTexto("Início (YYYY-MM-DD HH:mm): ");
        String fim = lerTexto("Fim (YYYY-MM-DD HH:mm): ");

        List<Opcao> opcoes = new ArrayList<>();
        char letra = 'a';
        System.out.println("Insira as opções (Vazio para terminar):");
        while (true) {
            String txt = lerTexto(letra + ") ");
            if (txt.isEmpty()) break;
            String correta = lerTexto("É a correta? (s/n): ");
            opcoes.add(new Opcao(String.valueOf(letra++), txt, correta.equalsIgnoreCase("s")));
        }
        return new MsgCriarPergunta(-1, enunc, ini, fim, opcoes);
    }

    public String formResponderPergunta(Pergunta p) {
        System.out.println("\nPERGUNTA: " + p.getEnunciado());
        for (Opcao o : p.getOpcoes()) {
            System.out.println(o.getLetra() + ") " + o.getTexto());
        }
        return lerTexto("Sua resposta (letra): ");
    }
}