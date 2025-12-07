package pt.isec.pd.tp.mensagens;

import pt.isec.pd.tp.estruturas.Docente;
import pt.isec.pd.tp.estruturas.Estudante;

public class MsgRegisto extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final Docente docente;
    private final Estudante estudante;
    private final String codigoDocente;


    public MsgRegisto(Docente docente, String codigoDocente) {
        this.docente = docente;
        this.estudante = null;
        this.codigoDocente = codigoDocente;
    }


    public MsgRegisto(Estudante estudante) {
        this.docente = null;
        this.estudante = estudante;
        this.codigoDocente = null;
    }

    public boolean isDocente() { return docente != null; }
    public boolean isEstudante() { return estudante != null; }

    public Docente getDocente() { return docente; }
    public Estudante getEstudante() { return estudante; }
    public String getCodigoDocente() { return codigoDocente; }
}