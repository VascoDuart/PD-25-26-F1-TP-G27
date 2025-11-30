import java.io.Serializable;

public class MsgObterEstatisticas extends Mensagem {
    private static final long serialVersionUID = 1L;
    
    private String codigo;

    public MsgObterEstatisticas(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }
}