package pt.isec.pd.tp.mensagens;

public class MsgResponderPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final int estudanteId; // Quem responde
    private final String codigoAcesso; // A que pergunta
    private final String letraOpcao; // O que escolheu

    public MsgResponderPergunta(int estudanteId, String codigoAcesso, String letraOpcao) {
        this.estudanteId = estudanteId;
        this.codigoAcesso = codigoAcesso;
        this.letraOpcao = letraOpcao;
    }

    public int getEstudanteId() { return estudanteId; }
    public String getCodigoAcesso() { return codigoAcesso; }
    public String getLetraOpcao() { return letraOpcao; }
}