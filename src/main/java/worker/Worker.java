package main.java.worker;

import main.java.comum.Logger;
import main.java.modelo.Mensagem;
import main.java.modelo.Tarefa;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;

/**
 * Representa um nó de processamento (Worker).
 * Suas responsabilidades são:
 * - Conectar-se e se registrar no orquestrador.
 * - Enviar sinais de vida (heartbeats) periodicamente.
 * - Receber tarefas do orquestrador.
 * - Simular o processamento da tarefa.
 * - Notificar o orquestrador sobre a conclusão da tarefa.
 * - Simular falhas aleatórias.
 */
public class Worker {

    // Gera um ID único para este worker.
    private final String id = "Worker-" + UUID.randomUUID().toString().substring(0, 8);
    private final String ipOrquestrador;
    private final int portaOrquestrador;
    private ObjectOutputStream saida; // Stream de saída para o orquestrador.
    private final Random random = new Random(); // Para simular tempo de processamento e falhas.

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java worker.Worker <ip_orquestrador> <porta_orquestrador>");
            return;
        }
        String ip = args[0];
        int porta = Integer.parseInt(args[1]);
        new Worker(ip, porta).iniciar();
    }

    public Worker(String ipOrquestrador, int portaOrquestrador) {
        this.ipOrquestrador = ipOrquestrador;
        this.portaOrquestrador = portaOrquestrador;
    }

    /**
     * Inicia a lógica principal do worker.
     */
    private void iniciar() {
        Logger.log(id, "Iniciando...");
        try (
            // Estabelece a conexão com o orquestrador.
            Socket socket = new Socket(ipOrquestrador, portaOrquestrador);
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())
        ) {
            this.saida = new ObjectOutputStream(socket.getOutputStream());

            // 1. Envia uma mensagem de registro.
            enviarMensagem(new Mensagem(Mensagem.TipoMensagem.REGISTRAR_WORKER, id));
            Logger.log(id, "Registrado com sucesso no orquestrador.");

            // 2. Agenda o envio periódico de heartbeats.
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::enviarHeartbeat, 0, 5, TimeUnit.SECONDS);

            // 3. Entra em um loop para aguardar e processar tarefas.
            while (true) {
                Mensagem msg = (Mensagem) entrada.readObject();
                if (msg.getTipo() == Mensagem.TipoMensagem.NOVA_TAREFA) {
                    Tarefa tarefa = (Tarefa) msg.getConteudo();
                    // O processamento da tarefa é bloqueante, mas poderia ser feito em outra thread se necessário.
                    processarTarefa(tarefa);
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            Logger.log(id, "Erro de conexão com o orquestrador: " + e.getMessage());
            // Se a conexão for perdida, o worker simplesmente encerra. O orquestrador detectará a falha.
        }
    }

    /**
     * Envia uma mensagem de heartbeat para o orquestrador.
     */
    private void enviarHeartbeat() {
        enviarMensagem(new Mensagem(Mensagem.TipoMensagem.HEARTBEAT, id));
    }

    /**
     * Simula o processamento de uma tarefa.
     * @param tarefa A tarefa a ser processada.
     */
    private void processarTarefa(Tarefa tarefa) {
        Logger.log(id, "Recebida tarefa " + tarefa.getId() + ". Iniciando processamento...");
        try {
            // Simula um tempo de processamento variável.
            int tempoDeProcessamento = 2000 + random.nextInt(8000); // Entre 2 e 10 segundos.
            Thread.sleep(tempoDeProcessamento);

            // Simula uma falha aleatória com 20% de chance.
            if (random.nextInt(100) < 20) {
                 Logger.log(id, "!!! SIMULANDO FALHA CRÍTICA !!! A tarefa " + tarefa.getId() + " não foi concluída.");
                 // Em uma falha real, o processo poderia travar. Aqui, encerramos o processo para simular o "crash".
                 System.exit(1);
            }

            // Se não falhou, a tarefa é concluída.
            Logger.log(id, "Tarefa " + tarefa.getId() + " processada com sucesso.");
            tarefa.setStatus(Tarefa.StatusTarefa.CONCLUIDA);
            
            // O worker não tem seu próprio relógio. Ele apenas anexa o timestamp que recebeu do orquestrador
            // para que o orquestrador possa atualizar seu próprio relógio corretamente.
            Mensagem msgConclusao = new Mensagem(Mensagem.TipoMensagem.TAREFA_CONCLUIDA, tarefa);
            msgConclusao.setTimestampLamport(tarefa.getTimestampLamport());

            enviarMensagem(msgConclusao);

        } catch (InterruptedException e) {
            Logger.log(id, "Processamento da tarefa " + tarefa.getId() + " interrompido.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Método utilitário sincronizado para enviar mensagens ao orquestrador.
     * @param msg A mensagem a ser enviada.
     */
    private synchronized void enviarMensagem(Mensagem msg) {
        try {
            if (saida != null) {
                saida.writeObject(msg);
                saida.flush();
            }
        } catch (IOException e) {
            Logger.log(id, "Falha ao enviar mensagem para o orquestrador: " + e.getMessage());
        }
    }
}

