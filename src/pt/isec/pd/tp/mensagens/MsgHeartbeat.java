package pt.isec.pd.tp.mensagens;

public class MsgHeartbeat extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final int versaoBD;
    private final int portoClienteTCP;
    private final int portoBDT_TCP;
    private final String querySQL;


    public MsgHeartbeat(int versaoBD, int portoClienteTCP, int portoBDT_TCP) {
        this.versaoBD = versaoBD;
        this.portoClienteTCP = portoClienteTCP;
        this.portoBDT_TCP = portoBDT_TCP;
        this.querySQL = null;
    }


    public MsgHeartbeat(int versaoBD, int portoClienteTCP, int portoBDT_TCP, String querySQL) {
        this.versaoBD = versaoBD;
        this.portoClienteTCP = portoClienteTCP;
        this.portoBDT_TCP = portoBDT_TCP;
        this.querySQL = querySQL;
    }

    public int getVersaoBD() { return versaoBD; }
    public int getPortoClienteTCP() { return portoClienteTCP; }
    public int getPortoBDT_TCP() { return portoBDT_TCP; }
    public String getQuerySQL() { return querySQL; }

    public boolean temQuery() { return querySQL != null; }
}
