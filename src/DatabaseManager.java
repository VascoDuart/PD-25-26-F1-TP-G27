import java.sql.*;
import java.io.File;

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

        // Ativar Foreign Keys
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    // 2. Criar as Tabelas (Se não existirem)
    public void criarTabelas() throws SQLException {
        // Tabela para guardar a Versão da BD (Essencial para a sincronização)
        String sqlConfig = "CREATE TABLE IF NOT EXISTS Configuracao (" +
                "id INTEGER PRIMARY KEY, " +
                "versao INTEGER NOT NULL DEFAULT 1, " +
                "codigo_docente_hash TEXT NOT NULL" +
                ");";

        // Tabela para Docentes
        String sqlDocente = "CREATE TABLE IF NOT EXISTS Docente (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nome TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL" +
                ");";

        // Tabela para Estudantes (NOVO)
        String sqlEstudante = "CREATE TABLE IF NOT EXISTS Estudante (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "numero_estudante TEXT UNIQUE NOT NULL, " +
                "nome TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL" +
                ");";

        // TODO: Faltam as tabelas Pergunta, Opcao e Resposta.

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlConfig);
            stmt.execute(sqlDocente);
            stmt.execute(sqlEstudante); // Executa a criação da tabela Estudante
            System.out.println("[BD] Tabelas verificadas/criadas (Configuracao, Docente, Estudante).");

            // Lógica inicial para guardar o código docente (pode ser hardcoded para a Meta 1)
            // if (!configuracaoExiste(conn)) {
            //    insertConfiguracaoInicial(conn, "HASH_DO_CODIGO_UNICO_AQUI");
            // }

        }
    }

    // 3. Persistência de Docente (inalterada)
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
            System.err.println("[BD] Erro ao inserir docente (email repetido?): " + e.getMessage());
            return false;
        }
    }

    // 4. Persistência de Estudante (NOVO)
    public synchronized boolean registarEstudante(Estudante e) {
        String sql = "INSERT INTO Estudante(numero_estudante, nome, email, password) VALUES(?,?,?,?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getNumEstudante()); // Único
            pstmt.setString(2, e.getNome());
            pstmt.setString(3, e.getEmail()); // Único
            pstmt.setString(4, e.getPassword());

            pstmt.executeUpdate();
            System.out.println("[BD] Estudante inserido: " + e.getEmail());
            return true;
        } catch (SQLException ex) {
            System.err.println("[BD] Erro ao inserir estudante (email ou número repetido?): " + ex.getMessage());
            return false;
        }
    }

    // 5. Autenticação de Docente (inalterada)
    public synchronized boolean autenticarDocente(String email, String password) {
        String sql = "SELECT password FROM Docente WHERE email = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String passNaBD = rs.getString("password");
                return passNaBD.equals(password);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro na autenticação de Docente: " + e.getMessage());
        }
        return false;
    }

    // 6. Autenticação de Estudante (NOVO)
    public synchronized boolean autenticarEstudante(String email, String password) {
        String sql = "SELECT password FROM Estudante WHERE email = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String passNaBD = rs.getString("password");
                return passNaBD.equals(password);
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro na autenticação de Estudante: " + e.getMessage());
        }
        return false;
    }


    // 7. Fechar a ligação
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