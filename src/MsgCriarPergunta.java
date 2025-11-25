import java.util.List;

public class MsgCriarPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final int docenteId; // ID do docente autenticado (obtido após login)
    private final String enunciado;
    private final String inicio;
    private final String fim;
    private final List<Opcao> opcoes;

    public MsgCriarPergunta(int docenteId, String enunciado, String inicio, String fim, List<Opcao> opcoes) {
        this.docenteId = docenteId;
        this.enunciado = enunciado;
        this.inicio = inicio;
        this.fim = fim;
        this.opcoes = opcoes;
    }

    public int getDocenteId() { return docenteId; }
    public String getEnunciado() { return enunciado; }
    public String getInicio() { return inicio; }
    public String getFim() { return fim; }
    public List<Opcao> getOpcoes() { return opcoes; }
}