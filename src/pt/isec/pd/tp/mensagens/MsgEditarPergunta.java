package pt.isec.pd.tp.mensagens;

public class MsgEditarPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;
    
    private final String codigoAcesso;
    private final String novoEnunciado;
    private final String novoInicio;
    private final String novoFim;
    // Nota: Editar opções é complexo em SQL puro, por isso focamo-nos nos dados principais
    // conforme é comum nesta fase do trabalho.

    public MsgEditarPergunta(String codigo, String enunc, String ini, String fim) {
        this.codigoAcesso = codigo;
        this.novoEnunciado = enunc;
        this.novoInicio = ini;
        this.novoFim = fim;
    }

    public String getCodigoAcesso() { return codigoAcesso; }
    public String getNovoEnunciado() { return novoEnunciado; }
    public String getNovoInicio() { return novoInicio; }
    public String getNovoFim() { return novoFim; }
}