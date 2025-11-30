import java.io.Serializable;

public class MsgEliminarPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;
    
    private String codigo;

    public MsgEliminarPergunta(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }
}