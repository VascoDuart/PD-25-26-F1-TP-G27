import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServicoDeDiretoria {

    // Lista thread-safe de servidores ativos, ordenada pela hora de registo
    private static final List<InfoServidor> servidoresAtivos =
            Collections.synchronizedList(new LinkedList<>());

    // Timeout fixo/hardcoded de 17 segundos
    private static final long TIMEOUT_HEARTBEAT_MS = 17000;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java ServicoDeDiretoria <porto_udp>");
            return;
        }

        int porto = Integer.parseInt(args[0]);

        // Inicia a thread de limpeza antes de escutar (tolerância a falhas)
        iniciarThreadDeLimpeza();

        try (DatagramSocket socket = new DatagramSocket(porto)) {
            System.out.println("[Diretoria] A escutar no porto " + porto + "...");

            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // --- TRATAMENTO ROBUSTO DE DESSERIALIZAÇÃO ---
                try (ObjectInputStream ois = new ObjectInputStream(
                        new ByteArrayInputStream(packet.getData(), 0, packet.getLength()))) {

                    Object msg = ois.readObject();

                    // CASO 1: Servidor (Registo ou Heartbeat)
                    if (msg instanceof MsgRegistoServidor) {
                        MsgRegistoServidor registo = (MsgRegistoServidor) msg;
                        processarRegistoOuHeartbeat(packet.getAddress(), registo, socket);
                    }
                    // CASO 2: Cliente (Pedido de Principal)
                    else if (msg instanceof MsgPedidoServidor) {
                        System.out.println("[Diretoria] CLIENTE pediu servidor: " + packet.getAddress().getHostAddress());
                        enviarResposta(socket, packet.getAddress(), packet.getPort());
                    }
                    // TODO: Adicionar lógica para MsgAnularRegisto (servidor a fechar ordenadamente)
                } catch (IOException | ClassNotFoundException e) {
                    // Ignora o pacote malformado (resolve o erro persistente)
                    System.err.println("[Diretoria - ERRO] Pacote inválido de " + packet.getAddress().getHostAddress() + ". A ignorar.");
                }
            }
        } catch (Exception e) {
            System.err.println("Erro fatal no ServicoDeDiretoria.");
            e.printStackTrace();
        }
    }

    private static void processarRegistoOuHeartbeat(InetAddress ip, MsgRegistoServidor registo, DatagramSocket socket) throws IOException {
        InfoServidor novoOuExistente = new InfoServidor(ip, registo.getPortoClienteTCP(), registo.getPortoBDT_TCP());

        synchronized (servidoresAtivos) {
            int index = servidoresAtivos.indexOf(novoOuExistente);

            if (index == -1) {
                // NOVO REGISTO: Adiciona ao fim da lista (o mais novo)
                servidoresAtivos.add(novoOuExistente);
                System.out.println("[Diretoria] NOVO REGISTO: " + ip.getHostAddress() + ". Total: " + servidoresAtivos.size());
            } else {
                // HEARTBEAT: Apenas atualiza o timestamp
                servidoresAtivos.get(index).setUltimoHeartbeat(System.currentTimeMillis());
                System.out.println("[Diretoria] Heartbeat recebido de: " + ip.getHostAddress());
            }

            // Envia a resposta (que contém o Principal atual) de volta para o Servidor
            enviarResposta(socket, ip, socket.getLocalPort());
        }
    }

    private static void enviarResposta(DatagramSocket socket, InetAddress ipDestino, int portoDestino) throws IOException {
        InfoServidor principal;
        synchronized (servidoresAtivos) {
            // O principal é o primeiro da lista (o mais antigo)
            principal = servidoresAtivos.isEmpty() ? null : servidoresAtivos.get(0);
        }

        // Constrói a mensagem com o IP/Portos do Principal
        MsgRespostaDiretoria resposta = new MsgRespostaDiretoria(
                principal != null ? principal.getIp() : null,
                principal != null ? principal.getPortoClienteTCP() : -1,
                principal != null ? principal.getPortoBDT_TCP() : -1
        );

        // Serialização e Envio
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(resposta);
        byte[] dataResp = baos.toByteArray();

        DatagramPacket respPacket = new DatagramPacket(
                dataResp, dataResp.length, ipDestino, portoDestino
        );
        socket.send(respPacket);
    }

    private static void iniciarThreadDeLimpeza() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Corre a cada 5 segundos para verificar o timeout de 17 segundos
        scheduler.scheduleAtFixedRate(() -> {
            long agora = System.currentTimeMillis();

            synchronized (servidoresAtivos) {
                servidoresAtivos.removeIf(servidor -> {
                    boolean expirou = (agora - servidor.getUltimoHeartbeat()) > TIMEOUT_HEARTBEAT_MS;
                    if (expirou) {
                        System.out.println("[Diretoria - Cleanup] Servidor expirado removido: " + servidor.getIp().getHostAddress());
                    }
                    return expirou;
                });

                if (!servidoresAtivos.isEmpty()) {
                    System.out.println("[Diretoria - Status] Servidores ativos: " + servidoresAtivos.size() + ". Principal: " + servidoresAtivos.get(0).getIp().getHostAddress());
                } else {
                    System.out.println("[Diretoria - Status] Lista de servidores vazia.");
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
}