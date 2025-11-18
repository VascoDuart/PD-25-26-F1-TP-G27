import java.sql.*;
import java.io.File;

public class DatabaseManager {

    private String dbPath;
    private Connection conn;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    // 1. Ligar à Base de Dados
    // 1. Ligar à Base de Dados (CORRIGIDO)
    public void conectar() throws SQLException {
        try {
            // FORÇAR O DRIVER A CARREGAR (O "truque" para resolver o teu erro)
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("O Driver SQLite não foi encontrado na biblioteca!");
        }

        String url = "jdbc:sqlite:" + this.dbPath;
        this.conn = DriverManager.getConnection(url); // Agora isto já vai funcionar
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
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "chave TEXT UNIQUE NOT NULL, " +
                "valor TEXT NOT NULL);";

        String sqlDocente = "CREATE TABLE IF NOT EXISTS Docente (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nome TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL);"; // Nota: Guardar hash na realidade

        String sqlEstudante = "CREATE TABLE IF NOT EXISTS Estudante (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "numero_estudante TEXT UNIQUE NOT NULL, " +
                "nome TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL);";

        // Inserir a versão 0 se a tabela estiver vazia
        String sqlInitVersao = "INSERT OR IGNORE INTO Configuracao (chave, valor) VALUES ('versao_bd', '0');";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlConfig);
            stmt.execute(sqlDocente);
            stmt.execute(sqlEstudante);
            stmt.execute(sqlInitVersao);
        }
        System.out.println("[BD] Tabelas verificadas/criadas.");
    }


    public synchronized boolean registarDocente(Docente d) {
        String sql = "INSERT INTO Docente(nome, email, password) VALUES(?,?,?)";

        // O 'try-with-resources' fecha o PreparedStatement automaticamente
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, d.getNome());
            pstmt.setString(2, d.getEmail());
            pstmt.setString(3, d.getPassword()); // Nota: Num caso real, usaríamos Hash aqui!

            pstmt.executeUpdate();
            System.out.println("[BD] Docente inserido: " + d.getEmail());
            return true;

        } catch (SQLException e) {
            System.err.println("[BD] Erro ao inserir docente (email repetido?): " + e.getMessage());
            return false;
        }
    }

    // 3. Fechar a ligação
    public void desconectar() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}