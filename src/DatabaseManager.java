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

    // ==================================================================================
    // 1. SEGURANÇA E UTILITÁRIOS
    // ==================================================================================

    /**
     * Gera o Hash SHA-256 de uma string (password ou código de docente).
     * @param password A string original em texto limpo.
     * @return A string codificada em Base64 do hash.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro critico: SHA-256 nao disponivel no sistema.", e);
        }
    }

    /**
     * Valida se o código introduzido no registo de docente corresponde ao hash guardado na BD.
     */
    public boolean validarCodigoDocente(String codigoFornecido) {
        String sql = "SELECT codigo_docente_hash FROM Configuracao WHERE id = 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String hashGuardada = rs.getString("codigo_docente_hash");
                return hashGuardada.equals(hashPassword(codigoFornecido));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ==================================================================================
    // 2. CONFIGURAÇÃO E LIGAÇÃO
    // ==================================================================================

    public void conectar() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("O Driver SQLite (JDBC) não foi encontrado na biblioteca!");
        }

        String url = "jdbc:sqlite:" + this.dbPath;
        this.conn = DriverManager.getConnection(url);

        // Ativa chaves estrangeiras (Foreign Keys) no SQLite
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        System.out.println("[BD] Ligação estabelecida a " + this.dbPath);
    }

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

    public void criarTabelas() throws SQLException {
        // Tabela de Configuração (Versão da BD e Código de Segurança)
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

            // Configuração Inicial: Define o código "DOCENTE2025" se a tabela estiver vazia
            String codigoHash = hashPassword("DOCENTE2025");
            String sqlInitConfig = "INSERT OR IGNORE INTO Configuracao (id, versao, codigo_docente_hash) VALUES (1, 0, '" + codigoHash + "');";
            stmt.execute(sqlInitConfig);

            System.out.println("[BD] Tabelas verificadas e configuração inicial assegurada.");
        }
    }

    /**
     * Incrementa a versão da base de dados.
     * Deve ser chamado após qualquer operação de escrita (INSERT/UPDATE/DELETE).
     */
    private void incrementarVersao() {
        String sql = "UPDATE Configuracao SET versao = versao + 1 WHERE id = 1";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("[BD] Erro crítico: Falha ao incrementar versão da BD: " + e.getMessage());
        }
    }

    // ==================================================================================
    // 3. GESTÃO DE UTILIZADORES (REGISTO E LOGIN)
    // ==================================================================================

    public synchronized boolean registarDocente(Docente d) {
        String sql = "INSERT INTO Docente(nome, email, password) VALUES(?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, d.getNome());
            pstmt.setString(2, d.getEmail());
            pstmt.setString(3, hashPassword(d.getPassword())); // HASH
            pstmt.executeUpdate();

            incrementarVersao(); // Atualiza versão
            System.out.println("[BD] Docente registado: " + d.getEmail());
            return true;
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao registar docente: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean registarEstudante(Estudante e) {
        String sql = "INSERT INTO Estudante(numero_estudante, nome, email, password) VALUES(?,?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getNumEstudante());
            pstmt.setString(2, e.getNome());
            pstmt.setString(3, e.getEmail());
            pstmt.setString(4, hashPassword(e.getPassword())); // HASH
            pstmt.executeUpdate();

            incrementarVersao(); // Atualiza versão
            System.out.println("[BD] Estudante registado: " + e.getEmail());
            return true;
        } catch (SQLException ex) {
            System.err.println("[BD] Erro ao registar estudante: " + ex.getMessage());
            return false;
        }
    }

    public synchronized boolean autenticarDocente(String email, String password) {
        String sql = "SELECT password FROM Docente WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Compara o Hash da password fornecida com o Hash na BD
                return rs.getString("password").equals(hashPassword(password));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Auxiliar: Obter ID interno do Docente
    public int obterIdDocente(String email) {
        String sql = "SELECT id FROM Docente WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {}
        return -1;
    }

    // Auxiliar: Obter ID interno do Estudante
    public int obterIdEstudante(String email) {
        String sql = "SELECT id FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {}
        return -1;
    }

    // ==================================================================================
    // 4. GESTÃO DE PERGUNTAS
    // ==================================================================================

    public synchronized boolean criarPergunta(int docenteId, String enunciado, String codAcesso, String inicio, String fim, List<Opcao> opcoes) {
        if (opcoes == null || opcoes.size() < 2) return false;

        try {
            conn.setAutoCommit(false); // Início da Transação

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

            if (perguntaId == -1) {
                conn.rollback();
                return false;
            }

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

            // Sucesso: Commit e Incremento de Versão
            incrementarVersao();
            conn.commit();
            conn.setAutoCommit(true);

            System.out.println("[BD] Pergunta criada: " + codAcesso);
            return true;

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao criar pergunta (rollback): " + e.getMessage());
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

                // Buscar opções
                List<Opcao> opcoes = obterOpcoes(pId);
                return new Pergunta(pId, enunc, codigo, ini, fim, opcoes);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter pergunta: " + e.getMessage());
        }
        return null;
    }

    private List<Opcao> obterOpcoes(int perguntaId) throws SQLException {
        List<Opcao> lista = new ArrayList<>();
        String sql = "SELECT letra_opcao, texto_opcao FROM Opcao WHERE pergunta_id = ? ORDER BY letra_opcao";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Nota: enviamos 'false' em isCorreta para não revelar a resposta ao aluno durante a obtenção
                lista.add(new Opcao(rs.getString("letra_opcao"), rs.getString("texto_opcao"), false));
            }
        }
        return lista;
    }

    // ==================================================================================
    // 5. GESTÃO DE RESPOSTAS
    // ==================================================================================

    public synchronized boolean registarResposta(int estudanteId, String codigoAcesso, String letra) {
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";
        String sqlInsert = "INSERT INTO Resposta(estudante_id, pergunta_id, opcao_escolhida, data_hora) VALUES(?,?,?,?)";

        try {
            // 1. Obter ID da Pergunta
            int perguntaId = -1;
            try (PreparedStatement ps1 = conn.prepareStatement(sqlId)) {
                ps1.setString(1, codigoAcesso);
                ResultSet rs = ps1.executeQuery();
                if (rs.next()) perguntaId = rs.getInt("id");
            }
            if (perguntaId == -1) return false;

            // 2. Inserir Resposta
            try (PreparedStatement ps2 = conn.prepareStatement(sqlInsert)) {
                ps2.setInt(1, estudanteId);
                ps2.setInt(2, perguntaId);
                ps2.setString(3, letra);
                ps2.setString(4, java.time.LocalDateTime.now().toString());
                ps2.executeUpdate();

                incrementarVersao(); // Update Versão
                System.out.println("[BD] Resposta registada. Aluno ID: " + estudanteId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao registar resposta (duplicada?): " + e.getMessage());
            return false;
        }
    }

    // ==================================================================================
    // 6. RELATÓRIOS E LISTAGENS (FASE 2)
    // ==================================================================================

    /**
     * Obtém a lista de perguntas criadas por um docente específico.
     */
    public List<Pergunta> obterPerguntasDoDocente(int docenteId) {
        List<Pergunta> lista = new ArrayList<>();
        String sql = "SELECT id, enunciado, codigo_acesso, inicio, fim FROM Pergunta WHERE docente_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, docenteId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int pId = rs.getInt("id");
                String enunc = rs.getString("enunciado");
                String cod = rs.getString("codigo_acesso");
                String ini = rs.getString("inicio");
                String fim = rs.getString("fim");

                // Retorna a pergunta sem opções (para listagem leve)
                lista.add(new Pergunta(pId, enunc, cod, ini, fim, new ArrayList<>()));
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao listar perguntas do docente: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Obtém a lista detalhada de respostas de uma pergunta para exportação CSV.
     * Inclui dados pessoais do estudante (JOIN).
     */
    public List<RespostaEstudante> obterRespostasDaPergunta(String codigoAcesso) {
        List<RespostaEstudante> lista = new ArrayList<>();

        // 1. Obter ID da Pergunta
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";
        int perguntaId = -1;
        try (PreparedStatement ps = conn.prepareStatement(sqlId)) {
            ps.setString(1, codigoAcesso);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) perguntaId = rs.getInt("id");
        } catch (SQLException e) { return lista; }

        if (perguntaId == -1) return lista;

        // 2. Query com JOIN (Resposta + Estudante)
        String sql = "SELECT e.numero_estudante, e.nome, e.email, r.opcao_escolhida " +
                "FROM Resposta r " +
                "JOIN Estudante e ON r.estudante_id = e.id " +
                "WHERE r.pergunta_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                lista.add(new RespostaEstudante(
                        rs.getString("numero_estudante"),
                        rs.getString("nome"),
                        rs.getString("email"),
                        rs.getString("opcao_escolhida")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter respostas para CSV: " + e.getMessage());
        }
        return lista;
    }
}