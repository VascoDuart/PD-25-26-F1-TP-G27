import java.io.Serializable;

public class MsgEliminarPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;
    private final String codigoAcesso;

    public MsgEliminarPergunta(String codigoAcesso) {
        this.codigoAcesso = codigoAcesso;
    }

    public String getCodigoAcesso() { return codigoAcesso; }
}