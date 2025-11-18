public class MsgRegisto extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final Docente docente;

    public MsgRegisto(Docente docente) {
        this.docente = docente;
    }

    public Docente getDocente() {
        return docente;
    }
}