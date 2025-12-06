package pt.isec.pd.tp.mensagens;

public class MsgObterEstatisticas extends Mensagem {
    private static final long serialVersionUID = 1L;
    private final String codigoAcesso;

    public MsgObterEstatisticas(String codigoAcesso) {
        this.codigoAcesso = codigoAcesso;
    }
    public String getCodigoAcesso() { return codigoAcesso; }
}