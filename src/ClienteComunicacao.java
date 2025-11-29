import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClienteComunicacao {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean conectado = false;

    private final ClienteVista vista;
    private final BlockingQueue<Object> filaRespostas = new LinkedBlockingQueue<>();

    public ClienteComunicacao(ClienteVista vista) {
        this.vista = vista;
    }

    public boolean conectar(String ip, int porto) {
        try {
            socket = new Socket(ip, porto);

            // CORREÇÃO CRÍTICA: Flush imediato
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            in = new ObjectInputStream(socket.getInputStream());

            conectado = true;
            new Thread(this::listenerLoop).start(); // Inicia escuta
            return true;
        } catch (IOException e) {
            System.err.println("[Coms] Erro ao conectar: " + e.getMessage());
            return false;
        }
    }

    private void listenerLoop() {
        try {
            while (conectado) {
                Object obj = in.readObject();

                if (obj instanceof String && ((String) obj).startsWith("NOTIFICACAO:")) {
                    vista.mostrarNotificacao(((String) obj).substring(12));
                } else {
                    filaRespostas.put(obj);
                }
            }
        } catch (Exception e) {
            conectado = false;
        }
    }

    public void enviar(Object obj) throws IOException {
        out.writeObject(obj);
        out.flush();
    }

    public Object receber() throws InterruptedException {
        return filaRespostas.take();
    }

    public void fechar() {
        conectado = false;
        try { if (socket != null) socket.close(); } catch (IOException e) {}
    }

    public boolean estaConectado() {
        return conectado;
    }
}