import java.sql.*;
import java.util.List;

public class DatabaseManager {

    private String dbPath;
    private Connection conn;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    // 1. Ligar à Base de Dados
    public void conectar() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("O Driver SQLite não foi encontrado na biblioteca!");
        }

        String url = "jdbc:sqlite:" + this.dbPath;
        this.conn = DriverManager.getConnection(url);
        System.out.println("[BD] Ligação estabelecida a " + this.dbPath);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    // 2. Criar as Tabelas
    public void criarTabelas() throws SQLException {
        String sqlConfig = "CREATE TABLE IF NOT EXISTS Configuracao (" +
                "id INTEGER PRIMARY KEY, " +
                "versao INTEGER NOT NULL DEFAULT 1, " +
                "codigo_docente_hash TEXT NOT NULL" +
                ");";

        String sqlDocente = "CREATE TABLE IF NOT EXISTS Docente (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nome TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL" +
                ");";

        String sqlEstudante = "CREATE TABLE IF NOT EXISTS Estudante (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "numero_estudante TEXT UNIQUE NOT NULL, " +
                "nome TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL" +
                ");";

        String sqlPergunta = "CREATE TABLE IF NOT EXISTS Pergunta (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "docente_id INTEGER NOT NULL, " +
                "enunciado TEXT NOT NULL, " +
                "codigo_acesso TEXT UNIQUE NOT NULL, " +
                "inicio TEXT NOT NULL, " +
                "fim TEXT NOT NULL, " +
                "FOREIGN KEY(docente_id) REFERENCES Docente(id)" +
                ");";

        String sqlOpcao = "CREATE TABLE IF NOT EXISTS Opcao (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "pergunta_id INTEGER NOT NULL, " +
                "letra_opcao TEXT NOT NULL, "
                + "texto_opcao TEXT NOT NULL, " +
                "opcao_correta BOOLEAN NOT NULL, " +
                "UNIQUE(pergunta_id, letra_opcao), " +
                "FOREIGN KEY(pergunta_id) REFERENCES Pergunta(id) ON DELETE CASCADE" +
                ");";

        String sqlResposta = "CREATE TABLE IF NOT EXISTS Resposta (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "estudante_id INTEGER NOT NULL, " +
                "pergunta_id INTEGER NOT NULL, " +
                "opcao_escolhida TEXT NOT NULL, " +
                "data_hora TEXT NOT NULL, " +
                "UNIQUE(estudante_id, pergunta_id), " +
                "FOREIGN KEY(estudante_id) REFERENCES Estudante(id), " +
                "FOREIGN KEY(pergunta_id) REFERENCES Pergunta(id)" +
                ");";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlConfig);
            stmt.execute(sqlDocente);
            stmt.execute(sqlEstudante);
            stmt.execute(sqlPergunta);
            stmt.execute(sqlOpcao);
            stmt.execute(sqlResposta);

            System.out.println("[BD] Tabelas verificadas/criadas (Docente, Estudante, Pergunta, Opcao).");
        }
    }

    // --- MÉTODOS DE PERSISTÊNCIA E AUTENTICAÇÃO ---

    public synchronized boolean registarDocente(Docente d) {
        String sql = "INSERT INTO Docente(nome, email, password) VALUES(?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, d.getNome());
            pstmt.setString(2, d.getEmail());
            pstmt.setString(3, d.getPassword());
            pstmt.executeUpdate();
            System.out.println("[BD] Docente inserido: " + d.getEmail());
            return true;
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao inserir docente: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean registarEstudante(Estudante e) {
        String sql = "INSERT INTO Estudante(numero_estudante, nome, email, password) VALUES(?,?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getNumEstudante());
            pstmt.setString(2, e.getNome());
            pstmt.setString(3, e.getEmail());
            pstmt.setString(4, e.getPassword());
            pstmt.executeUpdate();
            System.out.println("[BD] Estudante inserido: " + e.getEmail());
            return true;
        } catch (SQLException ex) {
            System.err.println("[BD] Erro ao inserir estudante: " + ex.getMessage());
            return false;
        }
    }

    public synchronized boolean autenticarDocente(String email, String password) {
        String sql = "SELECT password FROM Docente WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro na autenticação de Docente: " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean autenticarEstudante(String email, String password) {
        String sql = "SELECT password FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro na autenticação de Estudante: " + e.getMessage());
        }
        return false;
    }

    // --- MÉTODOS AUXILIARES DE ID (Corrigidos para não lançar exceções) ---

    public int obterIdDocente(String email) {
        String sql = "SELECT id FROM Docente WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro a obter ID do Docente: " + e.getMessage());
        }
        return -1; // Retorna -1 em caso de erro ou não encontrado
    }

    public int obterIdEstudante(String email) {
        String sql = "SELECT id FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro a obter ID do Estudante: " + e.getMessage());
        }
        return -1; // Retorna -1 em caso de erro ou não encontrado
    }

    // --- MÉTODOS DE NEGÓCIO (CRIAÇÃO DE PERGUNTAS) ---

    public synchronized boolean criarPergunta(int docenteId, String enunciado, String codAcesso, String inicio, String fim, List<Opcao> opcoes) {
        if (opcoes == null || opcoes.size() < 2) return false;

        try {
            conn.setAutoCommit(false);

            // 1. Inserir Pergunta
            String sqlP = "INSERT INTO Pergunta(docente_id, enunciado, codigo_acesso, inicio, fim) VALUES(?,?,?,?,?)";
            PreparedStatement pstmtP = conn.prepareStatement(sqlP, Statement.RETURN_GENERATED_KEYS);

            pstmtP.setInt(1, docenteId);
            pstmtP.setString(2, enunciado);
            pstmtP.setString(3, codAcesso);
            pstmtP.setString(4, inicio);
            pstmtP.setString(5, fim);
            pstmtP.executeUpdate();

            ResultSet rs = pstmtP.getGeneratedKeys();
            int perguntaId = rs.next() ? rs.getInt(1) : -1;

            if (perguntaId == -1) { conn.rollback(); conn.setAutoCommit(true); return false; }

            // 2. Inserir Opções
            String sqlO = "INSERT INTO Opcao(pergunta_id, letra_opcao, texto_opcao, opcao_correta) VALUES(?,?,?,?)";
            PreparedStatement pstmtO = conn.prepareStatement(sqlO);

            for (Opcao o : opcoes) {
                pstmtO.setInt(1, perguntaId);
                pstmtO.setString(2, o.getLetra());
                pstmtO.setString(3, o.getTexto());
                pstmtO.setBoolean(4, o.isCorreta());
                pstmtO.addBatch();
            }
            pstmtO.executeBatch();

            conn.commit();
            conn.setAutoCommit(true);
            System.out.println("[BD] Pergunta criada com sucesso (ID: " + perguntaId + ", Código: " + codAcesso + ").");
            return true;

        } catch (SQLException e) {
            System.err.println("[BD] Erro em criarPergunta (Rollback): " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ex) { System.err.println("Rollback falhou: " + ex.getMessage()); }
            try { conn.setAutoCommit(true); } catch (SQLException ex) {}
            return false;
        }
    }

    // ... dentro de DatabaseManager ...

    // 1. Buscar Pergunta pelo Código
    public Pergunta obterPerguntaPorCodigo(String codigo) {
        String sql = "SELECT id, enunciado, inicio, fim FROM Pergunta WHERE codigo_acesso = ?";
        Pergunta p = null;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, codigo);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int pId = rs.getInt("id");
                String enunc = rs.getString("enunciado");
                String ini = rs.getString("inicio");
                String fim = rs.getString("fim");

                // Agora buscar as opções
                List<Opcao> opcoes = obterOpcoes(pId);
                p = new Pergunta(enunc, ini, fim, opcoes);
                // Guardamos o ID na classe pergunta (se tiveres o setter)
                // p.setId(pId);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter pergunta: " + e.getMessage());
        }
        return p;
    }

    // Método auxiliar para buscar opções
    private List<Opcao> obterOpcoes(int perguntaId) throws SQLException {
        List<Opcao> lista = new java.util.ArrayList<>();
        String sql = "SELECT letra_opcao, texto_opcao FROM Opcao WHERE pergunta_id = ? ORDER BY letra_opcao";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Nota: Não enviamos se é correta ou não para o aluno não fazer batota!
                lista.add(new Opcao(rs.getString("letra_opcao"), rs.getString("texto_opcao"), false));
            }
        }
        return lista;
    }

    // 2. Gravar Resposta
    public synchronized boolean registarResposta(int estudanteId, String codigoAcesso, String letra) {
        // Primeiro descobrimos o ID da pergunta
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";

        // Depois inserimos (O tempo atual é automático ou passado pelo Java)
        String sqlInsert = "INSERT INTO Resposta(estudante_id, pergunta_id, opcao_escolhida, data_hora) VALUES(?,?,?,?)";

        try {
            // Passo 1: ID da Pergunta
            int perguntaId = -1;
            try (PreparedStatement ps1 = conn.prepareStatement(sqlId)) {
                ps1.setString(1, codigoAcesso);
                ResultSet rs = ps1.executeQuery();
                if (rs.next()) perguntaId = rs.getInt("id");
            }
            if (perguntaId == -1) return false;

            // Passo 2: Inserir Resposta
            try (PreparedStatement ps2 = conn.prepareStatement(sqlInsert)) {
                ps2.setInt(1, estudanteId);
                ps2.setInt(2, perguntaId);
                ps2.setString(3, letra);
                ps2.setString(4, java.time.LocalDateTime.now().toString()); // Data atual
                ps2.executeUpdate();
                System.out.println("[BD] Resposta registada para a pergunta " + perguntaId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao gravar resposta (já respondeu?): " + e.getMessage());
            return false;
        }
    }

    // --- FECHAR LIGAÇÃO ---

    public void desconectar() {
        try {
            if (conn != null) {
                conn.close();
                System.out.println("[BD] Ligação fechada.");
            }
        } catch (SQLException ex) {
            System.err.println("[BD] Erro ao fechar a ligação: " + ex.getMessage());
        }
    }
}