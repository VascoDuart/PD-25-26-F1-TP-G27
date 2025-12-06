package pt.isec.pd.tp.mensagens;

public class MsgObterRespostas extends Mensagem {
    private static final long serialVersionUID = 1L;
    
    private final String codigoAcesso; // Identifica a pergunta

    public MsgObterRespostas(String codigoAcesso) {
        this.codigoAcesso = codigoAcesso;
    }

    public String getCodigoAcesso() { return codigoAcesso; }
}