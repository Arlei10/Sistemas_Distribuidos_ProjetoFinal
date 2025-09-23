package main.java.orquestrador;

import main.java.comum.Logger;
import main.java.modelo.Mensagem;
import main.java.modelo.Tarefa;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orquestrador Secundário (Backup).
 * Sua função é manter uma cópia replicada do estado do Orquestrador Principal.
 * Ele escuta por atualizações de estado e, caso detecte uma falha do principal,
 * inicia o processo de failover.
 */
public class OrquestradorSecundario {

    // --- Configurações de Rede e Failover ---
    private static final int PORTA_SINCRONIZACAO = 5002;
    private static final String IP_PRINCIPAL = "localhost";
    private static final int PORTA_PRINCIPAL_CLIENTE = 5000;
    private static final int PORTA_PRINCIPAL_WORKER = 5001;
    private static final long TIMEOUT_FAILOVER_MS = 15000; // 15 segundos. Tempo máximo sem receber sincronização.

    // --- Estado Replicado ---
    // Usamos tipos atômicos e coleções concorrentes para segurança em threads.
    private Map<String, Tarefa> tarefasReplicadas = new ConcurrentHashMap<>();
    private Set<String> workersAtivosReplicados = ConcurrentHashMap.newKeySet();
    private final AtomicLong ultimoTimestampLamport = new AtomicLong(0);

    private final AtomicLong ultimaSincronizacao = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean principalAtivo = new AtomicBoolean(true);

    public static void main(String[] args) {
        new OrquestradorSecundario().iniciar();
    }

    /**
     * Inicia o orquestrador de backup.
     */
    private void iniciar() {
        Logger.log("Backup", "Iniciando Orquestrador de Backup na porta " + PORTA_SINCRONIZACAO);

        // Agenda uma verificação periódica da saúde do orquestrador principal.
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::verificarSaudePrincipal, TIMEOUT_FAILOVER_MS, TIMEOUT_FAILOVER_MS / 2, TimeUnit.MILLISECONDS);

        // Aguarda a conexão do orquestrador principal para receber atualizações de estado.
        try (ServerSocket serverSocket = new ServerSocket(PORTA_SINCRONIZACAO)) {
            while (principalAtivo.get()) {
                try (
                    Socket socketPrincipal = serverSocket.accept();
                    ObjectInputStream entrada = new ObjectInputStream(socketPrincipal.getInputStream())
                ) {
                    Logger.log("Backup", "Orquestrador Principal conectado para sincronização.");
                    ultimaSincronizacao.set(System.currentTimeMillis()); // Reseta o timer de timeout.
                    principalAtivo.set(true);

                    // Loop para ler mensagens de sincronização.
                    while (principalAtivo.get()) {
                        Mensagem msg = (Mensagem) entrada.readObject();
                        if (msg.getTipo() == Mensagem.TipoMensagem.SINCRONIZAR_ESTADO) {
                            atualizarEstado((Map<String, Object>) msg.getConteudo());
                            ultimaSincronizacao.set(System.currentTimeMillis());
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    Logger.log("Backup", "Conexão com principal perdida: " + e.getMessage());
                    principalAtivo.set(false); // Assume que o principal pode ter falhado.
                }
            }
        } catch (IOException e) {
            Logger.log("Backup", "Erro ao iniciar servidor de sincronização: " + e.getMessage());
        }
    }

    /**
     * Atualiza o estado interno do backup com os dados recebidos do principal.
     * @param estadoGlobal Um mapa contendo o estado das tarefas, workers e o relógio.
     */
    @SuppressWarnings("unchecked")
    private void atualizarEstado(Map<String, Object> estadoGlobal) {
        this.tarefasReplicadas = new ConcurrentHashMap<>((Map<String, Tarefa>) estadoGlobal.get("tarefas"));
        this.workersAtivosReplicados = ConcurrentHashMap.newKeySet();
        this.workersAtivosReplicados.addAll((Set<String>) estadoGlobal.get("workers"));
        this.ultimoTimestampLamport.set((long) estadoGlobal.get("relogio"));
        
        Logger.log("Backup", "Estado replicado atualizado. Tarefas: " + tarefasReplicadas.size() +
                ", Workers: " + workersAtivosReplicados.size() +
                ", Relógio: " + ultimoTimestampLamport.get());
    }

    /**
     * Verifica se o orquestrador principal ainda está ativo.
     * Se não receber uma mensagem de sincronização dentro do timeout, considera uma falha.
     */
    private void verificarSaudePrincipal() {
        if (System.currentTimeMillis() - ultimaSincronizacao.get() > TIMEOUT_FAILOVER_MS) {
            principalAtivo.set(false);
            Logger.log("Backup", "Principal não responde (timeout de sincronização). Tentando teste de conexão.");
            
            // Antes de declarar o failover, faz um teste ativo tentando se conectar às portas do principal.
            boolean clientePortaAberta = testarPorta(IP_PRINCIPAL, PORTA_PRINCIPAL_CLIENTE);
            boolean workerPortaAberta = testarPorta(IP_PRINCIPAL, PORTA_PRINCIPAL_WORKER);

            if (!clientePortaAberta && !workerPortaAberta) {
                Logger.log("Backup", "Principal parece estar offline. INICIANDO PROCESSO DE FAILOVER!");
                assumirComoPrincipal();
            } else {
                 Logger.log("Backup", "Principal ainda parece estar online. Aguardando próxima verificação.");
                 principalAtivo.set(true); // Reseta o status se as portas estiverem abertas, foi um falso alarme.
            }
        }
    }

    /**
     * Tenta abrir um socket para um host e porta para verificar se o serviço está ativo.
     * @param host O IP do host.
     * @param porta A porta a ser testada.
     * @return true se a conexão for bem-sucedida, false caso contrário.
     */
    private boolean testarPorta(String host, int porta) {
        try (Socket s = new Socket(host, porta)) {
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * Lógica para assumir o papel de orquestrador principal.
     * Nesta implementação simplificada, ele apenas se encerra e instrui o operador
     * a iniciar uma nova instância do Orquestrador principal.
     */
    private void assumirComoPrincipal() {
        Logger.log("Backup", "ASSUMINDO PAPEL DE ORQUESTRADOR PRINCIPAL.");
        
        // Em um sistema real, esta lógica seria mais complexa:
        // 1. O backup se promoveria a principal.
        // 2. Ele começaria a escutar nas portas de cliente e worker.
        // 3. Ele usaria o estado replicado (tarefasReplicadas, etc.) para continuar as operações.
        // Por simplicidade, aqui apenas sinalizamos a necessidade de intervenção manual.
        
        System.out.println("---------------------------------------------------------");
        System.out.println("FAILOVER: O Orquestrador Principal falhou.");
        System.out.println("Para completar o failover, inicie uma nova instância do");
        System.out.println("'Orquestrador.java'. Em uma implementação com persistência,");
        System.out.println("ele se recuperaria do último estado salvo.");
        System.out.println("O backup será encerrado.");
        System.out.println("---------------------------------------------------------");
        
        System.exit(0);
    }
}

