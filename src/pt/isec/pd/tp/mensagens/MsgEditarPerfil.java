package pt.isec.pd.tp.mensagens;

import pt.isec.pd.tp.estruturas.Docente;
import pt.isec.pd.tp.estruturas.Estudante;

public class MsgEditarPerfil extends Mensagem {
    private static final long serialVersionUID = 1L;

    // Apenas um será preenchido
    private final Docente novoDocente;
    private final Estudante novoEstudante;

    // Usamos o objeto pt.isec.pd.tp.bases.Docente/pt.isec.pd.tp.bases.Estudante (com os novos dados) e não apenas campos soltos.
    public MsgEditarPerfil(Docente novoDocente) {
        this.novoDocente = novoDocente;
        this.novoEstudante = null;
    }

    public MsgEditarPerfil(Estudante novoEstudante) {
        this.novoDocente = null;
        this.novoEstudante = novoEstudante;
    }

    public boolean isDocente() { return novoDocente != null; }
    public boolean isEstudante() { return novoEstudante != null; }

    public Docente getNovoDocente() { return novoDocente; }
    public Estudante getNovoEstudante() { return novoEstudante; }
}