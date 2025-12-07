package pt.isec.pd.tp.mensagens;

public class MsgResponderPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final int estudanteId;
    private final String codigoAcesso;
    private final String letraOpcao;

    public MsgResponderPergunta(int estudanteId, String codigoAcesso, String letraOpcao) {
        this.estudanteId = estudanteId;
        this.codigoAcesso = codigoAcesso;
        this.letraOpcao = letraOpcao;
    }

    public int getEstudanteId() { return estudanteId; }
    public String getCodigoAcesso() { return codigoAcesso; }
    public String getLetraOpcao() { return letraOpcao; }
}