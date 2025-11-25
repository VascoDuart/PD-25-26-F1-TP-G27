import java.io.Serializable;
import java.net.InetAddress;

public class InfoServidor implements Serializable {
    private static final long serialVersionUID = 1L;

    private final InetAddress ip;
    private final int portoClienteTCP;
    private final int portoBDT_TCP;
    private long ultimoHeartbeat; // Timestamp do último contato

    public InfoServidor(InetAddress ip, int portoClienteTCP, int portoBDT_TCP) {
        this.ip = ip;
        this.portoClienteTCP = portoClienteTCP;
        this.portoBDT_TCP = portoBDT_TCP;
        this.ultimoHeartbeat = System.currentTimeMillis();
    }

    // Getters e Setters
    public InetAddress getIp() { return ip; }
    public int getPortoClienteTCP() { return portoClienteTCP; }
    public int getPortoBDT_TCP() { return portoBDT_TCP; }
    public long getUltimoHeartbeat() { return ultimoHeartbeat; }
    public void setUltimoHeartbeat(long timestamp) { this.ultimoHeartbeat = timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfoServidor that = (InfoServidor) o;
        // Servidor é unicamente identificado por IP e Porto TCP de Cliente
        return portoClienteTCP == that.portoClienteTCP && ip.equals(that.ip);
    }

    @Override
    public int hashCode() {
        return 31 * ip.hashCode() + portoClienteTCP;
    }
}
