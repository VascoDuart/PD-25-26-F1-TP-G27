package pt.isec.pd.tp.estruturas;

import java.io.Serializable;

public class Estudante implements Serializable {
    private static final long serialVersionUID = 1L;

    private String numEstudante; // Único
    private String nome;
    private String email; // Único
    private String password;

    public Estudante(String numEstudante, String nome, String email, String password) {
        this.numEstudante = numEstudante;
        this.nome = nome;
        this.email = email;
        this.password = password;
    }

    public String getNumEstudante() { return numEstudante; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    @Override
    public String toString() {
        return "pt.isec.pd.tp.bases.Estudante{" + "numEstudante='" + numEstudante + '\'' + ", email='" + email + '\'' + '}';
    }
}