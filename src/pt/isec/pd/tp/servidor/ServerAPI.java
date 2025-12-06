package pt.isec.pd.tp.servidor;

public interface ServerAPI {
    void publicarAlteracao(String querySQL, int novaVersao);
    void notificarTodosClientes(String mensagem);
    Object getBDLock();
}
