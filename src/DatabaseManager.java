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

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro critico: SHA-256 nao disponivel no sistema.", e);
        }
    }

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

    public Connection getConnection() {
        return this.conn;
    }

    public void criarTabelas() throws SQLException {
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

            String codigoHash = hashPassword("DOCENTE2025");
            String sqlInitConfig = "INSERT OR IGNORE INTO Configuracao (id, versao, codigo_docente_hash) VALUES (1, 0, '" + codigoHash + "');";
            stmt.execute(sqlInitConfig);

            System.out.println("[BD] Tabelas verificadas e configuração inicial assegurada.");
        }
    }

    private int incrementarVersao() {
        String sql = "UPDATE Configuracao SET versao = versao + 1 WHERE id = 1";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return getVersaoBD();
        } catch (SQLException e) {
            System.err.println("[BD] Erro crítico: Falha ao incrementar versão da BD: " + e.getMessage());
            return -1;
        }
    }

    // ==================================================================================
    // 3. GESTÃO DE UTILIZADORES
    // ==================================================================================

    public synchronized String registarDocente(Docente d) {
        String sql = "INSERT INTO Docente(nome, email, password) VALUES(?,?,?)";
        String passHash = hashPassword(d.getPassword());

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, d.getNome());
            pstmt.setString(2, d.getEmail());
            pstmt.setString(3, passHash);
            pstmt.executeUpdate();

            incrementarVersao();
            System.out.println("[BD] Docente registado: " + d.getEmail());

            return String.format("INSERT INTO Docente(nome, email, password) VALUES('%s','%s','%s')",
                    d.getNome(), d.getEmail(), passHash);

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao registar docente: " + e.getMessage());
            return null;
        }
    }

    public synchronized String registarEstudante(Estudante e) {
        String sql = "INSERT INTO Estudante(numero_estudante, nome, email, password) VALUES(?,?,?,?)";
        String passHash = hashPassword(e.getPassword());

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getNumEstudante());
            pstmt.setString(2, e.getNome());
            pstmt.setString(3, e.getEmail());
            pstmt.setString(4, passHash);
            pstmt.executeUpdate();

            incrementarVersao();
            System.out.println("[BD] Estudante registado: " + e.getEmail());

            return String.format("INSERT INTO Estudante(numero_estudante, nome, email, password) VALUES('%s','%s','%s','%s')",
                    e.getNumEstudante(), e.getNome(), e.getEmail(), passHash);

        } catch (SQLException ex) {
            System.err.println("[BD] Erro ao registar estudante: " + ex.getMessage());
            return null;
        }
    }

    public synchronized boolean autenticarDocente(String email, String password) {
        String sql = "SELECT password FROM Docente WHERE email = ?";
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

    // ==================================================================================
    // 4. GESTÃO DE PERGUNTAS
    // ==================================================================================

    public synchronized String criarPergunta(int docenteId, String enunciado, String codAcesso, String inicio, String fim, List<Opcao> opcoes) {
        if (opcoes == null || opcoes.size() < 2) return null;

        String queryResultante = null;

        try {
            conn.setAutoCommit(false);

            String sqlP = "INSERT INTO Pergunta(docente_id, enunciado, codigo_acesso, inicio, fim) VALUES(?,?,?,?,?)";
            try (PreparedStatement pstmtP = conn.prepareStatement(sqlP, Statement.RETURN_GENERATED_KEYS)) {
                pstmtP.setInt(1, docenteId);
                pstmtP.setString(2, enunciado);
                pstmtP.setString(3, codAcesso);
                pstmtP.setString(4, inicio);
                pstmtP.setString(5, fim);
                pstmtP.executeUpdate();

                try (ResultSet rs = pstmtP.getGeneratedKeys()) {
                    if (rs.next()) {
                        int perguntaId = rs.getInt(1);

                        StringBuilder sqlOpcoes = new StringBuilder();
                        String sqlO = "INSERT INTO Opcao(pergunta_id, letra_opcao, texto_opcao, opcao_correta) VALUES(?,?,?,?)";
                        try (PreparedStatement pstmtO = conn.prepareStatement(sqlO)) {
                            for (Opcao o : opcoes) {
                                pstmtO.setInt(1, perguntaId);
                                pstmtO.setString(2, o.getLetra());
                                pstmtO.setString(3, o.getTexto());
                                pstmtO.setBoolean(4, o.isCorreta());
                                pstmtO.addBatch();

                                sqlOpcoes.append(String.format("INSERT INTO Opcao(pergunta_id, letra_opcao, texto_opcao, opcao_correta) VALUES(%d,'%s','%s',%d);",
                                        perguntaId, o.getLetra(), o.getTexto(), o.isCorreta() ? 1 : 0));
                            }
                            pstmtO.executeBatch();
                        }

                        queryResultante = String.format("INSERT INTO Pergunta(id, docente_id, enunciado, codigo_acesso, inicio, fim) VALUES(%d,%d,'%s','%s','%s','%s');%s",
                                perguntaId, docenteId, enunciado, codAcesso, inicio, fim, sqlOpcoes.toString());
                    } else {
                        conn.rollback();
                        return null;
                    }
                }
            }

            incrementarVersao();
            conn.commit();

            System.out.println("[BD] Pergunta criada: " + codAcesso);
            return queryResultante;

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao criar pergunta (rollback): " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ex) {}
            return null;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) {}
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
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter pergunta: " + e.getMessage());
        }
        return null;
    }

    private List<Opcao> obterOpcoes(int perguntaId) throws SQLException {
        List<Opcao> lista = new ArrayList<>();
        String sql = "SELECT letra_opcao, texto_opcao, opcao_correta FROM Opcao WHERE pergunta_id = ? ORDER BY letra_opcao";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                lista.add(new Opcao(rs.getString("letra_opcao"), rs.getString("texto_opcao"), rs.getBoolean("opcao_correta")));
            }
        }
        return lista;
    }

    // ==================================================================================
    // 5. GESTÃO DE RESPOSTAS
    // ==================================================================================

    public synchronized String registarResposta(int estudanteId, String codigoAcesso, String letra) {
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";
        String sqlInsert = "INSERT INTO Resposta(estudante_id, pergunta_id, opcao_escolhida, data_hora) VALUES(?,?,?,?)";
        String dataHora = java.time.LocalDateTime.now().toString();

        try {
            int perguntaId = -1;
            try (PreparedStatement ps1 = conn.prepareStatement(sqlId)) {
                ps1.setString(1, codigoAcesso);
                ResultSet rs = ps1.executeQuery();
                if (rs.next()) perguntaId = rs.getInt("id");
            }
            if (perguntaId == -1) return null;

            try (PreparedStatement ps2 = conn.prepareStatement(sqlInsert)) {
                ps2.setInt(1, estudanteId);
                ps2.setInt(2, perguntaId);
                ps2.setString(3, letra);
                ps2.setString(4, dataHora);
                ps2.executeUpdate();

                incrementarVersao();
                System.out.println("[BD] Resposta registada. Aluno ID: " + estudanteId);

                return String.format("INSERT INTO Resposta(estudante_id, pergunta_id, opcao_escolhida, data_hora) VALUES(%d,%d,'%s','%s')",
                        estudanteId, perguntaId, letra, dataHora);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao registar resposta (duplicada?): " + e.getMessage());
            return null;
        }
    }

    // ==================================================================================
    // 6. GESTÃO DO CLUSTER E REPLICAÇÃO
    // ==================================================================================

    public synchronized void executarQueryReplica(String querySQL) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(querySQL);
            System.out.println("[BD Backup] Query de replicação executada.");
        }
    }

    public int getVersaoBD() {
        String sql = "SELECT versao FROM Configuracao WHERE id = 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("versao");
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter versao: " + e.getMessage());
        }
        return -1;
    }

    public int obterDocenteIDDaPergunta(String codigoAcesso) {
        String sql = "SELECT docente_id FROM Pergunta WHERE codigo_acesso = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, codigoAcesso);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("docente_id");
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter ID do docente da pergunta: " + e.getMessage());
        }
        return -1;
    }

    // ==================================================================================
    // 7. RELATÓRIOS E LISTAGENS
    // ==================================================================================

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
                lista.add(new Pergunta(pId, enunc, cod, ini, fim, new ArrayList<>()));
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao listar perguntas do docente: " + e.getMessage());
        }
        return lista;
    }

    public List<RespostaEstudante> obterRespostasDaPergunta(String codigoAcesso) {
        List<RespostaEstudante> lista = new ArrayList<>();
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";
        int perguntaId = -1;
        try (PreparedStatement ps = conn.prepareStatement(sqlId)) {
            ps.setString(1, codigoAcesso);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) perguntaId = rs.getInt("id");
        } catch (SQLException e) { return lista; }

        if (perguntaId == -1) return lista;

        String sql = "SELECT e.numero_estudante, e.nome, e.email, r.opcao_escolhida " +
                "FROM Resposta r JOIN Estudante e ON r.estudante_id = e.id WHERE r.pergunta_id = ?";

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

    // ==================================================================================
    // 8. EDIÇÃO E ELIMINAÇÃO (MÉTODOS NOVOS ADICIONADOS)
    // ==================================================================================

    /**
     * Verifica se uma pergunta pode ser alterada.
     * Regras:
     * 1. A pergunta tem de existir e pertencer ao docenteId fornecido.
     * 2. A pergunta NÃO pode ter respostas associadas.
     */
    public boolean podeAlterarPergunta(String codigo, int docenteId) {
        // Query otimizada: verifica propriedade e conta respostas num só passo
        String sql = "SELECT p.id, (SELECT COUNT(*) FROM Resposta r WHERE r.pergunta_id = p.id) as total_respostas " +
                "FROM Pergunta p WHERE p.codigo_acesso = ? AND p.docente_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ps.setInt(2, docenteId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int totalRespostas = rs.getInt("total_respostas");
                // Retorna TRUE apenas se o total de respostas for 0
                return totalRespostas == 0;
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro na verificação de permissões: " + e.getMessage());
        }
        // Retorna FALSE se a pergunta não for encontrada, não pertencer ao docente ou tiver respostas
        return false;
    }

    public synchronized String eliminarPergunta(String codigo) {
        String sql = "DELETE FROM Pergunta WHERE codigo_acesso = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                incrementarVersao(); // Importante para o Heartbeat
                System.out.println("[BD] Pergunta eliminada: " + codigo);
                // Retorna a query SQL exata para os servidores de backup replicarem
                return "DELETE FROM Pergunta WHERE codigo_acesso = '" + codigo + "'";
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao eliminar pergunta: " + e.getMessage());
        }
        return null;
    }

    public synchronized String editarPergunta(String codigo, String enunciado, String inicio, String fim) {
        String sql = "UPDATE Pergunta SET enunciado=?, inicio=?, fim=? WHERE codigo_acesso=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, enunciado);
            ps.setString(2, inicio);
            ps.setString(3, fim);
            ps.setString(4, codigo);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                incrementarVersao(); // Importante para o Heartbeat
                System.out.println("[BD] Pergunta editada: " + codigo);
                // Retorna a query SQL exata para os servidores de backup replicarem
                return String.format("UPDATE Pergunta SET enunciado='%s', inicio='%s', fim='%s' WHERE codigo_acesso='%s'",
                        enunciado, inicio, fim, codigo);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao editar pergunta: " + e.getMessage());
        }
        return null;
    }
}