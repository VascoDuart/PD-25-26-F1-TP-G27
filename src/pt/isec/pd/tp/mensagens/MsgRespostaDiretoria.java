package pt.isec.pd.tp.mensagens;

import java.net.InetAddress;

public class MsgRespostaDiretoria extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final InetAddress ipServidorPrincipal;
    private final int portoClienteTCP;
    private final int portoBDT_TCP;

    public MsgRespostaDiretoria(InetAddress ip, int portoCliente, int portoDB) {
        this.ipServidorPrincipal = ip;
        this.portoClienteTCP = portoCliente;
        this.portoBDT_TCP = portoDB;
    }

    public InetAddress getIpServidorPrincipal() { return ipServidorPrincipal; }
    public int getPortoClienteTCP() { return portoClienteTCP; }
    public int getPortoBDT_TCP() { return portoBDT_TCP; }

    public boolean existeServidor() { return ipServidorPrincipal != null; }
}