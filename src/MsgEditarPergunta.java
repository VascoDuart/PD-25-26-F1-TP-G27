import java.io.Serializable;

public class MsgEditarPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;
    
    private String codigo;
    private String novoEnunciado;

    public MsgEditarPergunta(String codigo, String novoEnunciado) {
        this.codigo = codigo;
        this.novoEnunciado = novoEnunciado;
    }

    public String getCodigo() { return codigo; }
    public String getNovoEnunciado() { return novoEnunciado; }
}