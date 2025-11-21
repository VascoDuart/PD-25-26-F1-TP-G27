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

        // TODO: Faltam a tabela Resposta

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlConfig);
            stmt.execute(sqlDocente);
            stmt.execute(sqlEstudante);
            stmt.execute(sqlPergunta);
            stmt.execute(sqlOpcao);

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