import java.io.Serializable;

public class MsgObterPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;
    private final String codigoAcesso;

    public MsgObterPergunta(String codigoAcesso) {
        this.codigoAcesso = codigoAcesso;
    }

    public String getCodigoAcesso() { return codigoAcesso; }
}