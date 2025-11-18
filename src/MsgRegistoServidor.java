public class MsgRegistoServidor extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final int portoClienteTCP;
    private final int portoBDT_TCP;

    public MsgRegistoServidor(int portoClienteTCP, int portoBDT_TCP) {
        this.portoClienteTCP = portoClienteTCP;
        this.portoBDT_TCP = portoBDT_TCP;
    }

    public int getPortoClienteTCP() { return portoClienteTCP; }
    public int getPortoBDT_TCP() { return portoBDT_TCP; }
}