import java.io.Serializable;

public class Docente implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nome;
    private String email;
    private String password;

    public Docente(String nome, String email, String password) {
        this.nome = nome;
        this.email = email;
        this.password = password;
    }

    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return "Docente{" + "nome='" + nome + '\'' + ", email='" + email + '\'' + '}';
    }
}