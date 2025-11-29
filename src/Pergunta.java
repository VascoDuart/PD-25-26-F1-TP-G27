import java.io.Serializable;
import java.util.List;

public class Pergunta implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String enunciado;
    private String codigoAcesso;
    private String dataInicio;
    private String dataFim;
    private List<Opcao> opcoes;
    private int docenteId;

    // Construtor usado pelo Docente ao criar a pergunta (sem ID nem código ainda)
    public Pergunta(String enunciado, String inicio, String fim, List<Opcao> opcoes) {
        this.enunciado = enunciado;
        this.dataInicio = inicio;
        this.dataFim = fim;
        this.opcoes = opcoes;
    }

    // Construtor completo (usado quando vem da BD)
    public Pergunta(int id, String enunciado, String codigo, String inicio, String fim, List<Opcao> opcoes) {
        this.id = id;
        this.enunciado = enunciado;
        this.codigoAcesso = codigo;
        this.dataInicio = inicio;
        this.dataFim = fim;
        this.opcoes = opcoes;
    }

    // Getters e Setters necessários
    public String getEnunciado() { return enunciado; }
    public List<Opcao> getOpcoes() { return opcoes; }
    public String getCodigoAcesso() { return codigoAcesso; }
    public String getInicio() { return dataInicio; }
    public String getFim() { return dataFim; }
    public void setCodigoAcesso(String codigo) { this.codigoAcesso = codigo; }
    public void setId(int id) { this.id = id; }

    // Para debug
    @Override
    public String toString() {
        return "Pergunta{" + "enunciado='" + enunciado + '\'' + ", codigo='" + codigoAcesso + '\'' + '}';
    }
}