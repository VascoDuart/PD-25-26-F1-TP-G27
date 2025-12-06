package pt.isec.pd.tp.estruturas;

import java.io.Serializable;

public class RespostaEstudante implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String numEstudante;
    private String nome;
    private String email;
    private String opcaoEscolhida;

    public RespostaEstudante(String numEstudante, String nome, String email, String opcaoEscolhida) {
        this.numEstudante = numEstudante;
        this.nome = nome;
        this.email = email;
        this.opcaoEscolhida = opcaoEscolhida;
    }

    public String getNumEstudante() { return numEstudante; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getOpcaoEscolhida() { return opcaoEscolhida; }
}