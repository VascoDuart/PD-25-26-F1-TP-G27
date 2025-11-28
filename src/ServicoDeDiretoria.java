import java.io.*;
import java.net.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

// Classe Auxiliar para guardar o estado de cada servidor
class ServidorInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public final InetAddress ip;
    public final int portoClienteTCP;
    public final int portoBDT_TCP;

    public volatile long ultimoHeartbeat; // Timestamp do último HB recebido
    public volatile int versaoBD;        // Última versão reportada no HB
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

    // 17 segundos fixos para timeout (requisito do enunciado)
    private static final int TIMEOUT_MS = 17000;

    // Armazena os servidores ativos, usando o porto Cliente TCP como chave única (para simplificar)
    private final Map<Integer, ServidorInfo> servidoresAtivos = new ConcurrentHashMap<>();

    private DatagramSocket socket;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java ServicoDeDiretoria <porto_udp>");
            return;
        }

        int porto = Integer.parseInt(args[0]);
        new ServicoDeDiretoria().iniciar(porto);
    }

    public void iniciar(int porto) {
        try {
            this.socket = new DatagramSocket(porto);
            System.out.println("[Diretoria] A escutar no porto " + porto + "...");

            // 1. Inicia a Thread de Monitorização de Timeout (cleanup)
            iniciarMonitorizacao();

            // 2. Loop principal para receber Heartbeats e Pedidos
            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Processa a mensagem
                processarMensagem(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) socket.close();
        }
    }

    // ==================================================================================
    // LÓGICA DE RECEÇÃO E PROCESSAMENTO (Thread Principal)
    // ==================================================================================

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
                // Cliente a pedir o Principal
                System.out.println("[Diretoria] CLIENTE pediu servidor: " + ipRemoto);
            }
            // Envia a resposta (seja registo, Heartbeat ou pedido de cliente, a resposta é sempre a mesma: quem é o Principal)
            enviarResposta(ipRemoto, portoRemoto);

        } catch (EOFException | SocketException e) {
            System.err.println("[Diretoria] Erro ao ler objeto do pacote: " + e.getMessage());
        }
    }

    private void processarRegisto(MsgRegistoServidor registo, InetAddress ip) {
        // Se o servidor já está registado, é um re-registo (pode ocorrer em caso de falha de comunicação)
        if (servidoresAtivos.containsKey(registo.getPortoClienteTCP())) {
            // Apenas atualiza o timestamp
            servidoresAtivos.get(registo.getPortoClienteTCP()).ultimoHeartbeat = System.currentTimeMillis();
            System.out.println("[Diretoria] SERVIDOR já registado (HB): " + ip + ":" + registo.getPortoClienteTCP());
            return;
        }

        // NOVO REGISTO
        boolean isPrincipal = servidoresAtivos.isEmpty();
        ServidorInfo novoServidor = new ServidorInfo(
                ip,
                registo.getPortoClienteTCP(),
                registo.getPortoBDT_TCP(),
                0, // Versão inicial da BD é 0
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
            System.out.println("[Diretoria] HB ignorado: Servidor não registado. IP: " + ip);
            return; // Servidor não está na lista de ativos, ignora.
        }

        // Atualiza o estado
        info.ultimoHeartbeat = System.currentTimeMillis();
        info.versaoBD = heartbeat.getVersaoBD();
    }

    // ==================================================================================
    // LÓGICA DE MONITORIZAÇÃO E FAILOVER (Thread Secundária)
    // ==================================================================================

    private void iniciarMonitorizacao() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // Verifica a cada 5 segundos
                    long agora = System.currentTimeMillis();

                    // Itera e remove servidores que excederam o timeout
                    servidoresAtivos.entrySet().removeIf(entry -> {
                        ServidorInfo info = entry.getValue();

                        if (agora - info.ultimoHeartbeat > TIMEOUT_MS) {
                            System.out.println("[Diretoria] Servidor inativo removido: " + info.ip + ":" + info.portoClienteTCP);

                            // SE O PRINCIPAL FALHOU:
                            if (info.isPrincipal) {
                                System.err.println("[Diretoria] ERRO CRÍTICO: Principal expirou. Iniciando promoção...");
                                promoverBackup();
                            }
                            return true; // Remove da coleção
                        }
                        return false;
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    /**
     * Lógica para promover o Backup com a versão de BD mais recente (ou o mais antigo se as versões forem iguais)
     */
    private void promoverBackup() {
        // Encontra o servidor que não é Principal e tem a maior versão da BD
        ServidorInfo novoPrincipal = servidoresAtivos.values().stream()
                .filter(info -> !info.isPrincipal)
                // Se as versões forem iguais, o primeiro a ser inserido será 'promovido' (não é estritamente o mais antigo na BD)
                .max(Comparator.comparingInt(s -> s.versaoBD))
                .orElse(null);

        if (novoPrincipal != null) {
            novoPrincipal.isPrincipal = true;
            // NOTA: Devemos atualizar todos os clientes (com Heartbeat) sobre o novo Principal
            System.out.println("[Diretoria] PROMOÇÃO: Backup em porto " + novoPrincipal.portoClienteTCP + " promovido a Principal. Versão BD: " + novoPrincipal.versaoBD);
        } else {
            // Ninguém mais está ativo (o Principal caiu e não há backups)
            System.err.println("[Diretoria] Falha total: Nenhum Servidor ativo disponível.");
        }
    }


    // ==================================================================================
    // LÓGICA DE RESPOSTA (Comum a todas as requisições)
    // ==================================================================================

    private void enviarResposta(InetAddress ipDestino, int portoDestino) throws IOException {

        // Tenta encontrar o Principal atual na lista ativa
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