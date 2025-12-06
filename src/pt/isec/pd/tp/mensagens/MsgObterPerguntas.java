package pt.isec.pd.tp.mensagens;

public class MsgObterPerguntas extends Mensagem {
    private static final long serialVersionUID = 1L;

    // Filtros possíveis: "TODAS", "ATIVAS", "FUTURAS", "EXPIRADAS"
    private final String filtro;

    public MsgObterPerguntas(String filtro) {
        this.filtro = filtro;
    }

    public String getFiltro() { return filtro; }
}