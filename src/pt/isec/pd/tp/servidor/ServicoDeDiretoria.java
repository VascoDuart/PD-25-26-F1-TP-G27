package pt.isec.pd.tp.servidor;

import pt.isec.pd.tp.mensagens.MsgHeartbeat;
import pt.isec.pd.tp.mensagens.MsgPedidoServidor;
import pt.isec.pd.tp.mensagens.MsgRegistoServidor;
import pt.isec.pd.tp.mensagens.MsgRespostaDiretoria;

import java.io.*;
import java.net.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;


class ServidorInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public final InetAddress ip;
    public final int portoClienteTCP;
    public final int portoBDT_TCP;

    public volatile long ultimoHeartbeat;
    public volatile int versaoBD;
    public volatile boolean isPrincipal;

    public ServidorInfo(InetAddress ip, int portoCliente, int portoDB, int versaoBD, boolean isPrincipal) {
        this.ip = ip;
        this.portoClienteTCP = portoCliente;
        this.portoBDT_TCP = portoDB;
        this.versaoBD = versaoBD;
        this.isPrincipal = isPrincipal;
        this.ultimoHeartbeat = System.currentTimeMillis();
    }
}


public class ServicoDeDiretoria {


    private static final int TIMEOUT_MS = 17000;


    private final Map<Integer, ServidorInfo> servidoresAtivos = new ConcurrentHashMap<>();

    private DatagramSocket socket;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java pt.isec.pd.tp.servidor.ServicoDeDiretoria <porto_udp>");
            return;
        }

        int porto = Integer.parseInt(args[0]);
        new ServicoDeDiretoria().iniciar(porto);
    }

    public void iniciar(int porto) {
        try {
            this.socket = new DatagramSocket(porto);
            System.out.println("[Diretoria] A escutar no porto " + porto + "...");


            iniciarMonitorizacao();


            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);


                processarMensagem(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) socket.close();
        }
    }



    private synchronized void processarMensagem(DatagramPacket packet) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()))) {
            Object msg = ois.readObject();
            InetAddress ipRemoto = packet.getAddress();
            int portoRemoto = packet.getPort();

            if (msg instanceof MsgRegistoServidor) {
                MsgRegistoServidor registo = (MsgRegistoServidor) msg;
                processarRegisto(registo, ipRemoto);
            }
            else if (msg instanceof MsgHeartbeat) {
                MsgHeartbeat heartbeat = (MsgHeartbeat) msg;
                processarHeartbeat(heartbeat, ipRemoto);
            }
            else if (msg instanceof MsgPedidoServidor) {

                System.out.println("[Diretoria] CLIENTE pediu servidor: " + ipRemoto);
            }

            enviarResposta(ipRemoto, portoRemoto);

        } catch (EOFException | SocketException e) {
            System.err.println("[Diretoria] Erro ao ler objeto do pacote: " + e.getMessage());
        }
    }

    private void processarRegisto(MsgRegistoServidor registo, InetAddress ip) {

        if (servidoresAtivos.containsKey(registo.getPortoClienteTCP())) {

            servidoresAtivos.get(registo.getPortoClienteTCP()).ultimoHeartbeat = System.currentTimeMillis();
            System.out.println("[Diretoria] SERVIDOR já registado (HB): " + ip + ":" + registo.getPortoClienteTCP());
            return;
        }


        boolean isPrincipal = servidoresAtivos.isEmpty();
        ServidorInfo novoServidor = new ServidorInfo(
                ip,
                registo.getPortoClienteTCP(),
                registo.getPortoBDT_TCP(),
                0,
                isPrincipal
        );

        servidoresAtivos.put(registo.getPortoClienteTCP(), novoServidor);

        if (isPrincipal) {
            System.out.println(" -> Novo Principal definido: " + ip + ":" + registo.getPortoClienteTCP());
        } else {
            System.out.println("[Diretoria] SERVIDOR registou-se como BACKUP: " + ip + ":" + registo.getPortoClienteTCP());
        }
    }

    private void processarHeartbeat(MsgHeartbeat heartbeat, InetAddress ip) {
        ServidorInfo info = servidoresAtivos.get(heartbeat.getPortoClienteTCP());

        if (info == null) {
            System.out.println("[Diretoria] HB ignorado: pt.isec.pd.tp.servidor.Servidor não registado. IP: " + ip);
            return;
        }


        info.ultimoHeartbeat = System.currentTimeMillis();
        info.versaoBD = heartbeat.getVersaoBD();
    }



    private void iniciarMonitorizacao() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    long agora = System.currentTimeMillis();


                    servidoresAtivos.entrySet().removeIf(entry -> {
                        ServidorInfo info = entry.getValue();

                        if (agora - info.ultimoHeartbeat > TIMEOUT_MS) {
                            System.out.println("[Diretoria] pt.isec.pd.tp.servidor.Servidor inativo removido: " + info.ip + ":" + info.portoClienteTCP);


                            if (info.isPrincipal) {
                                System.err.println("[Diretoria] ERRO CRÍTICO: Principal expirou. Iniciando promoção...");
                                promoverBackup();
                            }
                            return true;
                        }
                        return false;
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }


    private void promoverBackup() {

        ServidorInfo novoPrincipal = servidoresAtivos.values().stream()
                .filter(info -> !info.isPrincipal)

                .max(Comparator.comparingInt(s -> s.versaoBD))
                .orElse(null);

        if (novoPrincipal != null) {
            novoPrincipal.isPrincipal = true;

            System.out.println("[Diretoria] PROMOÇÃO: Backup em porto " + novoPrincipal.portoClienteTCP + " promovido a Principal. Versão BD: " + novoPrincipal.versaoBD);
        } else {

            System.err.println("[Diretoria] Falha total: Nenhum pt.isec.pd.tp.servidor.Servidor ativo disponível.");
        }
    }



    private void enviarResposta(InetAddress ipDestino, int portoDestino) throws IOException {


        ServidorInfo principal = servidoresAtivos.values().stream()
                .filter(info -> info.isPrincipal)
                .findFirst()
                .orElse(null);

        InetAddress ipPrincipal = (principal != null) ? principal.ip : null;
        int portoCliente = (principal != null) ? principal.portoClienteTCP : -1;
        int portoDB = (principal != null) ? principal.portoBDT_TCP : -1;

        MsgRespostaDiretoria resposta = new MsgRespostaDiretoria(
                ipPrincipal, portoCliente, portoDB
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(resposta);
        byte[] dataResp = baos.toByteArray();

        DatagramPacket respPacket = new DatagramPacket(
                dataResp, dataResp.length, ipDestino, portoDestino
        );
        socket.send(respPacket);
    }
}