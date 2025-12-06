import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ClienteVista {
    private final Scanner scanner;

    public ClienteVista() {
        this.scanner = new Scanner(System.in);
    }

    public void mostrarMensagem(String msg) {
        System.out.println("[INFO] " + msg);
    }

    public void mostrarErro(String msg) {
        System.err.println("[ERRO] " + msg);
    }

    public void mostrarNotificacao(String msg) {
        System.out.println("\n>>> NOTIFICAÇÃO: " + msg + " <<<\n> ");
    }

    public void mostrarListaPerguntas(List<Pergunta> lista) {
        System.out.println("\n--- As suas Perguntas ---");
        for (Pergunta p : lista) {
            System.out.println("- Código: " + p.getCodigoAcesso() +
                    " | Enunciado: " + p.getEnunciado());
        }
        System.out.println("-------------------------");
    }

    public String lerTexto(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public int lerInteiro(String prompt) {
        try {
            System.out.print(prompt);
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) { return -1; }
    }

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
        System.out.println("2. Consultar Perguntas");
        System.out.println("3. Editar Pergunta");
        System.out.println("4. Eliminar Pergunta");
        System.out.println("5. Ver Estatísticas");
        System.out.println("6. Exportar CSV");
        System.out.println("7. Editar Perfil");
        System.out.println("0. Logout");
        return lerInteiro("Opção: ");
    }

    public int menuEstudante() {
        System.out.println("\n=== ESTUDANTE ===");
        System.out.println("1. Responder a Pergunta");
        System.out.println("2. Ver Histórico");
        System.out.println("3. Editar Perfil");
        System.out.println("0. Logout");
        return lerInteiro("Opção: ");
    }

    public String escolherFiltro() {
        System.out.println("Filtro: [1] Ativas | [2] Futuras | [3] Expiradas | [0] Todas");
        int op = lerInteiro("> ");
        switch (op) {
            case 1: return "ATIVAS";
            case 2: return "FUTURAS";
            case 3: return "EXPIRADAS";
            default: return "TODAS";
        }
    }

    public MsgLogin formLogin() {
        return new MsgLogin(lerTexto("Email: "), lerTexto("Password: "));
    }

    public MsgRegisto formRegistoDocente() {
        System.out.println("--- Novo Docente ---");
        return new MsgRegisto(new Docente(lerTexto("Nome: "), lerTexto("Email: "), lerTexto("Password: ")), lerTexto("Código Institucional: "));
    }

    public MsgRegisto formRegistoEstudante() {
        System.out.println("--- Novo Estudante ---");
        return new MsgRegisto(new Estudante(lerTexto("Nº Estudante: "), lerTexto("Nome: "), lerTexto("Email: "), lerTexto("Password: ")));
    }

    public MsgCriarPergunta formCriarPergunta() {
        String enunc = lerTexto("Enunciado: ");
        String ini = lerTexto("Início (YYYY-MM-DD HH:MM): ");
        String fim = lerTexto("Fim (YYYY-MM-DD HH:MM): ");
        List<Opcao> opcoes = new ArrayList<>();
        char letra = 'a';
        System.out.println("Opções (Vazio para terminar):");
        while (true) {
            String txt = lerTexto(letra + ") ");
            if (txt.isEmpty()) break;
            opcoes.add(new Opcao(String.valueOf(letra++), txt, lerTexto("Correta? (s/n): ").equalsIgnoreCase("s")));
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

    public MsgEditarPerfil formEditarPerfil(int tipoUtilizador, String emailAtual) {
        System.out.println("\n--- EDIÇÃO DE PERFIL ---");
        String novoNome = lerTexto("Novo Nome: ");
        String novaPass = lerTexto("Nova Password: "); // Senha deve ser alterada

        // Se a password ou nome for vazio, mantém a lógica atual. Aqui assumimos que o utilizador
        // está a fornecer valores que substituirão os antigos, ou a repeti-los se não for para alterar.
        if (novoNome.isEmpty()) novoNome = "NAO_ALTERAR";
        if (novaPass.isEmpty()) novaPass = "NAO_ALTERAR";

        if (tipoUtilizador == 1) { // Docente
            // No Docente, não há campo extra (Nº Estudante), usamos placeholder para o email
            Docente d = new Docente(novoNome, emailAtual, novaPass);
            return new MsgEditarPerfil(d);
        } else { // Estudante
            String novoNumEstudante = lerTexto("Novo Nº Estudante: ");
            if (novoNumEstudante.isEmpty()) novoNumEstudante = "NAO_ALTERAR";

            Estudante e = new Estudante(novoNumEstudante, novoNome, emailAtual, novaPass);
            return new MsgEditarPerfil(e);
        }
    }
}