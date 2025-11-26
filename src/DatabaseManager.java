import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class DatabaseManager {

    private String dbPath;
    private Connection conn;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro critico: SHA-256 nao disponivel", e);
        }
    }

    // --- CONEXÃO E SETUP ---
    public void conectar() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver SQLite em falta.");
        }
        String url = "jdbc:sqlite:" + this.dbPath;
        this.conn = DriverManager.getConnection(url);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    public void criarTabelas() throws SQLException {
        // Tabela de Configuração (Versão e Hash do Código Docente)
        String sqlConfig = "CREATE TABLE IF NOT EXISTS Configuracao (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "versao INTEGER NOT NULL DEFAULT 0, " +
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
                "letra_opcao TEXT NOT NULL, " +
                "texto_opcao TEXT NOT NULL, " +
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

            // Inicializar Configuração (Código Docente: "DOCENTE2025")
            String codigoHash = hashPassword("DOCENTE2025");
            String sqlInitConfig = "INSERT OR IGNORE INTO Configuracao (id, versao, codigo_docente_hash) VALUES (1, 0, '" + codigoHash + "');";
            stmt.execute(sqlInitConfig);
        }
    }

    // --- GESTÃO DE VERSÃO (CLUSTER) ---
    private void incrementarVersao() {
        String sql = "UPDATE Configuracao SET versao = versao + 1 WHERE id = 1";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao incrementar versao: " + e.getMessage());
        }
    }

    // --- VALIDAÇÕES E REGISTOS ---
    public boolean validarCodigoDocente(String codigoFornecido) {
        String sql = "SELECT codigo_docente_hash FROM Configuracao WHERE id = 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String hashGuardada = rs.getString("codigo_docente_hash");
                return hashGuardada.equals(hashPassword(codigoFornecido));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public synchronized boolean registarDocente(Docente d) {
        String sql = "INSERT INTO Docente(nome, email, password) VALUES(?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, d.getNome());
            pstmt.setString(2, d.getEmail());
            pstmt.setString(3, hashPassword(d.getPassword())); // Hash aqui
            pstmt.executeUpdate();
            incrementarVersao(); // Update Versão
            return true;
        } catch (SQLException e) {
            System.err.println("[BD] Erro registar docente: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean registarEstudante(Estudante e) {
        String sql = "INSERT INTO Estudante(numero_estudante, nome, email, password) VALUES(?,?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getNumEstudante());
            pstmt.setString(2, e.getNome());
            pstmt.setString(3, e.getEmail());
            pstmt.setString(4, hashPassword(e.getPassword())); // Hash aqui
            pstmt.executeUpdate();
            incrementarVersao(); // Update Versão
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    public synchronized boolean autenticarDocente(String email, String password) {
        String sql = "SELECT password FROM Docente WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Compara Hash gerado com Hash guardado
                return rs.getString("password").equals(hashPassword(password));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public synchronized boolean autenticarEstudante(String email, String password) {
        String sql = "SELECT password FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password").equals(hashPassword(password));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // --- AUXILIARES ID ---
    public int obterIdDocente(String email) {
        String sql = "SELECT id FROM Docente WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {}
        return -1;
    }

    public int obterIdEstudante(String email) {
        String sql = "SELECT id FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {}
        return -1;
    }

    // --- PERGUNTAS E RESPOSTAS ---
    public synchronized boolean criarPergunta(int docenteId, String enunciado, String codAcesso, String inicio, String fim, List<Opcao> opcoes) {
        try {
            conn.setAutoCommit(false);
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
            if (perguntaId == -1) throw new SQLException("Falha ID Pergunta");

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

            incrementarVersao(); // Update Versão
            conn.commit();
            conn.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ex) {}
            return false;
        }
    }

    public Pergunta obterPerguntaPorCodigo(String codigo) {
        String sql = "SELECT id, enunciado, inicio, fim FROM Pergunta WHERE codigo_acesso = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, codigo);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int pId = rs.getInt("id");
                String enunc = rs.getString("enunciado");
                String ini = rs.getString("inicio");
                String fim = rs.getString("fim");
                List<Opcao> opcoes = obterOpcoes(pId);
                return new Pergunta(pId, enunc, codigo, ini, fim, opcoes);
            }
        } catch (SQLException e) {}
        return null;
    }

    private List<Opcao> obterOpcoes(int perguntaId) throws SQLException {
        List<Opcao> lista = new ArrayList<>();
        String sql = "SELECT letra_opcao, texto_opcao FROM Opcao WHERE pergunta_id = ? ORDER BY letra_opcao";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                lista.add(new Opcao(rs.getString("letra_opcao"), rs.getString("texto_opcao"), false));
            }
        }
        return lista;
    }

    public synchronized boolean registarResposta(int estudanteId, String codigoAcesso, String letra) {
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";
        String sqlInsert = "INSERT INTO Resposta(estudante_id, pergunta_id, opcao_escolhida, data_hora) VALUES(?,?,?,?)";
        try {
            int perguntaId = -1;
            try (PreparedStatement ps1 = conn.prepareStatement(sqlId)) {
                ps1.setString(1, codigoAcesso);
                ResultSet rs = ps1.executeQuery();
                if (rs.next()) perguntaId = rs.getInt("id");
            }
            if (perguntaId == -1) return false;

            try (PreparedStatement ps2 = conn.prepareStatement(sqlInsert)) {
                ps2.setInt(1, estudanteId);
                ps2.setInt(2, perguntaId);
                ps2.setString(3, letra);
                ps2.setString(4, java.time.LocalDateTime.now().toString());
                ps2.executeUpdate();
                incrementarVersao(); // Update Versão
                return true;
            }
        } catch (SQLException e) { return false; }
    }
}