package pt.isec.pd.tp.mensagens;

public class MsgEditarPergunta extends Mensagem {
    private static final long serialVersionUID = 1L;
    
    private final String codigoAcesso;
    private final String novoEnunciado;
    private final String novoInicio;
    private final String novoFim;


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