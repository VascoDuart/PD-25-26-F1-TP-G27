package pt.isec.pd.tp;

import pt.isec.pd.tp.estruturas.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class DatabaseManager {
    private static final java.time.format.DateTimeFormatter FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            System.out.println("[BD] pt.isec.pd.tp.bases.Docente registado: " + d.getEmail());

            return String.format("INSERT INTO pt.isec.pd.tp.bases.Docente(nome, email, password) VALUES('%s','%s','%s')",
                    d.getNome(), d.getEmail(), passHash);

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao registar docente: " + e.getMessage());
            return null;
        }
    }

    // --- NOVO: EDIÇÃO DE PERFIL DOCENTE ---
    public synchronized String editarDocente(String emailAntigo, String novoNome, String novaPass) {
        String passHash = hashPassword(novaPass);
        String sql = "UPDATE Docente SET nome = ?, password = ? WHERE email = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, novoNome);
            pstmt.setString(2, passHash);
            pstmt.setString(3, emailAntigo);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                incrementarVersao();
                System.out.println("[BD] pt.isec.pd.tp.bases.Docente editado: " + emailAntigo);
                return String.format("UPDATE pt.isec.pd.tp.bases.Docente SET nome='%s', password='%s' WHERE email='%s'",
                        novoNome, passHash, emailAntigo);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao editar docente: " + e.getMessage());
        }
        return null;
    }
    // --- FIM NOVO ---

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
            System.out.println("[BD] pt.isec.pd.tp.bases.Estudante registado: " + e.getEmail());

            return String.format("INSERT INTO pt.isec.pd.tp.bases.Estudante(numero_estudante, nome, email, password) VALUES('%s','%s','%s','%s')",
                    e.getNumEstudante(), e.getNome(), e.getEmail(), passHash);

        } catch (SQLException ex) {
            System.err.println("[BD] Erro ao registar estudante: " + ex.getMessage());
            return null;
        }
    }

    // --- NOVO: EDIÇÃO DE PERFIL ESTUDANTE ---
    public synchronized String editarEstudante(String emailAntigo, String novoNum, String novoNome, String novaPass) {
        String passHash = hashPassword(novaPass);
        // Usamos o email (chave de sessão) e atualizamos os campos
        String sql = "UPDATE Estudante SET numero_estudante = ?, nome = ?, password = ? WHERE email = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, novoNum);
            pstmt.setString(2, novoNome);
            pstmt.setString(3, passHash);
            pstmt.setString(4, emailAntigo);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                incrementarVersao();
                System.out.println("[BD] pt.isec.pd.tp.bases.Estudante editado: " + emailAntigo);
                return String.format("UPDATE pt.isec.pd.tp.bases.Estudante SET numero_estudante='%s', nome='%s', password='%s' WHERE email='%s'",
                        novoNum, novoNome, passHash, emailAntigo);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao editar estudante: " + e.getMessage());
        }
        return null;
    }
    // --- FIM NOVO ---


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
        } catch (SQLException e) {
        }
        return -1;
    }

    public int obterIdEstudante(String email) {
        String sql = "SELECT id FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
        }
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

                                sqlOpcoes.append(String.format("INSERT INTO pt.isec.pd.tp.bases.Opcao(pergunta_id, letra_opcao, texto_opcao, opcao_correta) VALUES(%d,'%s','%s',%d);",
                                        perguntaId, o.getLetra(), o.getTexto(), o.isCorreta() ? 1 : 0));
                            }
                            pstmtO.executeBatch();
                        }

                        queryResultante = String.format("INSERT INTO pt.isec.pd.tp.bases.Pergunta(id, docente_id, enunciado, codigo_acesso, inicio, fim) VALUES(%d,%d,'%s','%s','%s','%s');%s",
                                perguntaId, docenteId, enunciado, codAcesso, inicio, fim, sqlOpcoes.toString());
                    } else {
                        conn.rollback();
                        return null;
                    }
                }
            }

            incrementarVersao();
            conn.commit();

            System.out.println("[BD] pt.isec.pd.tp.bases.Pergunta criada: " + codAcesso);
            return queryResultante;

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao criar pergunta (rollback): " + e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException ex) {
            }
            return null;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
            }
        }
    }

    public boolean isPerguntaExpirada(String codigoAcesso) {
        String sql = "SELECT fim FROM Pergunta WHERE codigo_acesso = ?";
        try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, codigoAcesso);
            java.sql.ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String fimStr = rs.getString("fim");
                java.time.LocalDateTime fim = java.time.LocalDateTime.parse(fimStr.trim(), FORMATTER);
                java.time.LocalDateTime agora = java.time.LocalDateTime.now();

                return fim.isBefore(agora);
            }
        } catch (java.sql.SQLException e) {
            System.err.println("[BD] Erro ao verificar expiração: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[BD] Erro de parsing de data: " + e.getMessage());
        }
        return false;
    }

    // Dentro da classe pt.isec.pd.tp.DatabaseManager

    public boolean isPerguntaAtiva(String codigoAcesso) {
        String sql = "SELECT inicio, fim FROM Pergunta WHERE codigo_acesso = ?";
        try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, codigoAcesso);
            java.sql.ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String inicioStr = rs.getString("inicio");
                String fimStr = rs.getString("fim");

                // As datas devem ser trimmed para evitar erros de parsing de espaço em branco
                java.time.LocalDateTime inicio = java.time.LocalDateTime.parse(inicioStr.trim(), FORMATTER);
                java.time.LocalDateTime fim = java.time.LocalDateTime.parse(fimStr.trim(), FORMATTER);
                java.time.LocalDateTime agora = java.time.LocalDateTime.now();

                // A pergunta é ATIVA se: (Início <= Agora) E (Fim >= Agora)
                return !inicio.isAfter(agora) && !fim.isBefore(agora);
            }
        } catch (java.sql.SQLException e) {
            System.err.println("[BD] Erro ao verificar estado: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[BD] Erro de parsing de data: " + e.getMessage());
        }
        return false;
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



    public List<RespostaEstudante> obterRespostasDaPergunta(String codigoAcesso) {
        List<RespostaEstudante> lista = new ArrayList<>();
        String sqlId = "SELECT id FROM Pergunta WHERE codigo_acesso = ?";
        int perguntaId = -1;

        // 1. Obter ID da pt.isec.pd.tp.bases.Pergunta (Usando try-with-resources para garantir o fecho do ResultSet)
        try (PreparedStatement ps = conn.prepareStatement(sqlId)) {
            ps.setString(1, codigoAcesso);

            // O ResultSet da primeira consulta agora está em seu próprio try-with-resources
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) perguntaId = rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao obter ID da pergunta: " + e.getMessage());
            return lista;
        }

        if (perguntaId == -1) {
            System.out.println("[DEBUG DB] pt.isec.pd.tp.bases.Pergunta com código " + codigoAcesso + " não encontrada.");
            return lista;
        }

        // 2. Query com JOIN (Resposta + pt.isec.pd.tp.bases.Estudante)
        System.out.println("[DEBUG DB] Iniciando consulta de respostas..."); // LOG 1

        String sql = "SELECT e.numero_estudante, e.nome, e.email, r.opcao_escolhida " +
                "FROM Resposta r JOIN Estudante e ON r.estudante_id = e.id WHERE r.pergunta_id = ?";

        // O PreparedStatement e o ResultSet da segunda consulta também usam try-with-resources
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[DEBUG DB] Consulta SQL executada. Lendo resultados..."); // LOG 2

                while (rs.next()) {
                    lista.add(new RespostaEstudante(
                            rs.getString("numero_estudante"),
                            rs.getString("nome"),
                            rs.getString("email"),
                            rs.getString("opcao_escolhida")
                    ));
                }

                System.out.println("[DEBUG DB] Fim da leitura. Total de respostas lidas: " + lista.size()); // LOG 3

            } // Fecho automático do ResultSet
        } catch (SQLException e) {
            System.err.println("[BD] Erro fatal ao obter respostas: " + e.getMessage());
        } // Fecho automático do PreparedStatement

        System.out.println("[DEBUG DB] Retornando lista de respostas."); // LOG 4
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
                System.out.println("[BD] pt.isec.pd.tp.bases.Pergunta eliminada: " + codigo);
                // Retorna a query SQL exata para os servidores de backup replicarem
                return "DELETE FROM pt.isec.pd.tp.bases.Pergunta WHERE codigo_acesso = '" + codigo + "'";
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
                System.out.println("[BD] pt.isec.pd.tp.bases.Pergunta editada: " + codigo);
                // Retorna a query SQL exata para os servidores de backup replicarem
                return String.format("UPDATE pt.isec.pd.tp.bases.Pergunta SET enunciado='%s', inicio='%s', fim='%s' WHERE codigo_acesso='%s'",
                        enunciado, inicio, fim, codigo);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao editar pergunta: " + e.getMessage());
        }
        return null;
    }

    // ... (restante código) ...

    // --- NOVA FUNCIONALIDADE: HISTÓRICO ---
    public List<HistoricoItem> obterHistoricoEstudante(int estudanteId) {
        List<HistoricoItem> lista = new ArrayList<>();
        // Query: Junta Resposta, pt.isec.pd.tp.bases.Pergunta e pt.isec.pd.tp.bases.Opcao.
        // Filtra apenas perguntas onde o prazo (fim) já passou.
        String sql = "SELECT p.enunciado, p.codigo_acesso, r.data_hora, r.opcao_escolhida, " +
                "(SELECT o.opcao_correta FROM Opcao o WHERE o.pergunta_id = p.id AND o.letra_opcao = r.opcao_escolhida) as acertou " +
                "FROM Resposta r " +
                "JOIN Pergunta p ON r.pergunta_id = p.id " +
                "WHERE r.estudante_id = ? AND datetime(p.fim) < datetime('now', 'localtime')";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, estudanteId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                lista.add(new HistoricoItem(
                        rs.getString("enunciado"),
                        rs.getString("codigo_acesso"),
                        rs.getString("data_hora"),
                        rs.getString("opcao_escolhida"),
                        rs.getBoolean("acertou")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro histórico: " + e.getMessage());
        }
        return lista;
    }

    // --- NOVA FUNCIONALIDADE: ESTATÍSTICAS ---
    public String obterEstatisticas(String codigoAcesso) {
        int pId = -1;
        // 1. Descobrir ID da pergunta
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM Pergunta WHERE codigo_acesso = ?")) {
            ps.setString(1, codigoAcesso);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) pId = rs.getInt(1);
            else return "pt.isec.pd.tp.bases.Pergunta não encontrada.";
        } catch (Exception e) {
            return "Erro BD.";
        }

        // 2. Calcular Totais
        String sqlTotal = "SELECT COUNT(*) FROM Resposta WHERE pergunta_id = ?";
        String sqlCertas = "SELECT COUNT(*) FROM Resposta r " +
                "JOIN Opcao o ON r.pergunta_id = o.pergunta_id AND r.opcao_escolhida = o.letra_opcao " +
                "WHERE r.pergunta_id = ? AND o.opcao_correta = 1"; // 1 é true no SQLite

        try {
            int total = 0;
            int certas = 0;

            try (PreparedStatement ps1 = conn.prepareStatement(sqlTotal)) {
                ps1.setInt(1, pId);
                ResultSet rs1 = ps1.executeQuery();
                if (rs1.next()) total = rs1.getInt(1);
            }

            if (total == 0) return "Sem respostas submetidas ainda.";

            try (PreparedStatement ps2 = conn.prepareStatement(sqlCertas)) {
                ps2.setInt(1, pId);
                ResultSet rs2 = ps2.executeQuery();
                if (rs2.next()) certas = rs2.getInt(1);
            }

            double percentagem = ((double) certas / total) * 100;
            return String.format("Total Respostas: %d | Certas: %d | Taxa de Sucesso: %.1f%%", total, certas, percentagem);

        } catch (SQLException e) {
            return "Erro ao calcular estatísticas: " + e.getMessage();
        }
    }


    public List<Pergunta> listarPerguntasComFiltro(int docenteId, String filtro) {
        List<Pergunta> lista = new ArrayList<>();
        String sql = "SELECT id, enunciado, codigo_acesso, inicio, fim FROM Pergunta WHERE docente_id = ? ORDER BY inicio DESC";

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        java.time.LocalDateTime agora = java.time.LocalDateTime.now();

        // LIMPEZA DO FILTRO (Para garantir que não há lixo)
        if (filtro == null) filtro = "TODAS";
        filtro = filtro.trim().toUpperCase();

        System.out.println("\n=== DEBUG FILTRO ===");
        System.out.println("Filtro recebido: '" + filtro + "'");
        System.out.println("Data Atual: " + agora);

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, docenteId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String cod = rs.getString("codigo_acesso");
                String inicioStr = rs.getString("inicio");
                String fimStr = rs.getString("fim");

                boolean adicionar = false;
                String motivo = "Rejeitado";

                try {
                    java.time.LocalDateTime inicio = java.time.LocalDateTime.parse(inicioStr.trim(), formatter);
                    java.time.LocalDateTime fim = java.time.LocalDateTime.parse(fimStr.trim(), formatter);

                    switch (filtro) {
                        case "ATIVAS":
                            // Começou antes de agora E acaba depois de agora
                            if (!inicio.isAfter(agora) && !fim.isBefore(agora)) {
                                adicionar = true;
                                motivo = "Aceite (Está a decorrer)";
                            } else {
                                motivo = "Rejeitado (Não está no intervalo)";
                            }
                            break;

                        case "FUTURAS":
                            if (inicio.isAfter(agora)) {
                                adicionar = true;
                                motivo = "Aceite (Começa no futuro)";
                            } else {
                                motivo = "Rejeitado (Já começou)";
                            }
                            break;

                        case "EXPIRADAS":
                            if (fim.isBefore(agora)) {
                                adicionar = true;
                                motivo = "Aceite (Já acabou)";
                            } else {
                                motivo = "Rejeitado (Ainda não acabou)";
                            }
                            break;

                        default:
                            adicionar = true;
                            motivo = "Aceite (Filtro TODOS ou Inválido: " + filtro + ")";
                            break;
                    }

                    System.out.println(" -> pt.isec.pd.tp.bases.Pergunta [" + cod + "] (" + inicioStr + " - " + fimStr + "): " + motivo);

                } catch (Exception e) {
                    System.out.println(" -> pt.isec.pd.tp.bases.Pergunta [" + cod + "]: ERRO DATA - " + e.getMessage());
                }

                if (adicionar) {
                    lista.add(new Pergunta(rs.getInt("id"), rs.getString("enunciado"), cod, inicioStr, fimStr, new ArrayList<>()));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("====================\n");
        return lista;
    }
}