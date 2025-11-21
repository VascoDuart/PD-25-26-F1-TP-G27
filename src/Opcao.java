import java.io.Serializable;

public class Opcao implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String letra;
    private final String texto;
    private final boolean correta;

    public Opcao(String letra, String texto, boolean correta) {
        this.letra = letra;
        this.texto = texto;
        this.correta = correta;
    }

    public String getLetra() { return letra; }
    public String getTexto() { return texto; }
    public boolean isCorreta() { return correta; }
}