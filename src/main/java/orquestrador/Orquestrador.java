package main.java.orquestrador;

import main.java.comum.Logger;
import main.java.comum.RelogioLamport;
import main.java.modelo.Credenciais;
import main.java.modelo.Mensagem;
import main.java.modelo.Tarefa;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

/**
 * Classe principal do Orquestrador.
 * É o cérebro do sistema, responsável por:
 * - Receber e autenticar clientes.
 * - Aceitar registro de workers.
 * - Distribuir tarefas aos workers (balanceamento de carga).
 * - Monitorar a saúde dos workers (heartbeat).
 * - Lidar com falhas de workers e redistribuir suas tarefas.
 * - Sincronizar seu estado com um orquestrador de backup para tolerância a falhas.
 */
public class Orquestrador {

    // --- Configurações de Rede ---
    private static final int PORTA_CLIENTE = 5000;    // Porta para escutar conexões de clientes.
    private static final int PORTA_WORKER = 5001;     // Porta para escutar conexões de workers.
    private static final String IP_BACKUP = "localhost"; // IP do orquestrador de backup.
    private static final int PORTA_BACKUP = 5002;     // Porta para se conectar ao backup.
    private static final long TIMEOUT_HEARTBEAT_MS = 10000; // 10 segundos. Tempo máximo sem heartbeat antes de considerar um worker inativo.

    // --- Estado Global do Sistema ---
    // Mapas que usam implementações concorrentes para serem seguros em ambiente com múltiplas threads.
    private final Map<String, Tarefa> tarefas = new ConcurrentHashMap<>(); // Mapeia ID da tarefa para o objeto Tarefa.
    private final Map<String, InfoWorker> workersAtivos = new ConcurrentHashMap<>(); // Mapeia ID do worker para suas informações de conexão.
    private final List<String> workerIdsOrdenados = new CopyOnWriteArrayList<>(); // Lista de IDs de workers para a política de Round Robin.
    private final Map<String, String> usuariosAutenticados = new ConcurrentHashMap<>(); // Mapeia token para nome de usuário.
    private final Map<String, String> credenciaisUsuarios = Map.of("cliente1", "senha123", "cliente2", "senha456"); // "Banco de dados" de usuários.

    // --- Controle e Comunicação ---
    private int proximoWorkerIndex = 0;             // Índice para a política de Round Robin.
    private final RelogioLamport relogio = new RelogioLamport(); // Relógio lógico para ordenar eventos.
    private ObjectOutputStream saidaBackup;         // Stream de saída para o orquestrador de backup.

    public static void main(String[] args) throws IOException {
        new Orquestrador().iniciar();
    }

    /**
     * Inicia todos os serviços do orquestrador.
     */
    private void iniciar() {
        Logger.log("Orquestrador", "Iniciando o Orquestrador Principal...");
        conectarAoBackup();

        // Agenda uma tarefa periódica para verificar a saúde dos workers.
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::verificarWorkersAtivos, TIMEOUT_HEARTBEAT_MS, TIMEOUT_HEARTBEAT_MS, TimeUnit.MILLISECONDS);

        // Inicia uma thread para escutar conexões de workers.
        new Thread(this::ouvirWorkers).start();

        // Usa a thread principal para escutar conexões de clientes.
        ouvirClientes();
    }

    /**
     * Tenta se conectar ao orquestrador de backup em uma thread separada para não bloquear a inicialização principal.
     */
    private void conectarAoBackup() {
        new Thread(() -> {
            boolean conectado = false;
            while (!conectado) {
                try {
                    Socket socketBackup = new Socket(IP_BACKUP, PORTA_BACKUP);
                    this.saidaBackup = new ObjectOutputStream(socketBackup.getOutputStream());
                    conectado = true;
                    Logger.log("Orquestrador", "Conectado com sucesso ao Orquestrador de Backup.");
                    sincronizarEstadoComBackup(); // Envia o estado atual assim que conecta.
                } catch (IOException e) {
                    Logger.log("Orquestrador", "Aguardando Orquestrador de Backup ficar online...");
                    try {
                        Thread.sleep(5000); // Espera 5 segundos antes de tentar novamente.
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }
    
    /**
     * Loop infinito que aguarda e aceita conexões de workers na porta designada.
     */
    private void ouvirWorkers() {
        try (ServerSocket serverSocket = new ServerSocket(PORTA_WORKER)) {
            Logger.log("Orquestrador", "Aguardando workers na porta " + PORTA_WORKER);
            while (true) {
                Socket socketWorker = serverSocket.accept();
                // Para cada worker que se conecta, uma nova thread é criada para lidar com a comunicação.
                new Thread(new ManipuladorWorker(socketWorker)).start();
            }
        } catch (IOException e) {
            Logger.log("Orquestrador", "Erro ao ouvir workers: " + e.getMessage());
        }
    }

    /**
     * Loop infinito que aguarda e aceita conexões de clientes na porta designada.
     */
    private void ouvirClientes() {
        try (ServerSocket serverSocket = new ServerSocket(PORTA_CLIENTE)) {
            Logger.log("Orquestrador", "Aguardando clientes na porta " + PORTA_CLIENTE);
            while (true) {
                Socket socketCliente = serverSocket.accept();
                // Para cada cliente, uma nova thread é criada para lidar com a comunicação.
                new Thread(new ManipuladorCliente(socketCliente)).start();
            }
        } catch (IOException e) {
            Logger.log("Orquestrador", "Erro fatal no servidor de clientes: " + e.getMessage());
        }
    }

    /**
     * Distribui uma tarefa para um worker disponível usando a política de Round Robin.
     * Este método é sincronizado para evitar condições de corrida ao acessar a lista de workers.
     * @param tarefa A tarefa a ser distribuída.
     */
    private synchronized void distribuirTarefa(Tarefa tarefa) {
        if (workersAtivos.isEmpty()) {
            Logger.log("Orquestrador", "Nenhum worker disponível. Tarefa " + tarefa.getId() + " em espera.");
            tarefa.setStatus(Tarefa.StatusTarefa.AGUARDANDO);
            return;
        }

        // --- Política de Balanceamento: Round Robin ---
        // Pega o próximo worker da lista e avança o índice de forma circular.
        String workerId = workerIdsOrdenados.get(proximoWorkerIndex);
        proximoWorkerIndex = (proximoWorkerIndex + 1) % workerIdsOrdenados.size();
        
        InfoWorker worker = workersAtivos.get(workerId);
        if (worker != null) {
            try {
                // Atualiza o estado da tarefa antes de enviá-la.
                tarefa.setStatus(Tarefa.StatusTarefa.EXECUTANDO);
                tarefa.setIdWorker(workerId);
                tarefa.setTimestampLamport(relogio.tick()); // Atribui um timestamp de evento.
                
                Mensagem msg = new Mensagem(Mensagem.TipoMensagem.NOVA_TAREFA, tarefa);
                msg.setTimestampLamport(relogio.getTempo());

                // Envia a mensagem para o worker.
                worker.getSaida().writeObject(msg);
                worker.getSaida().flush();

                Logger.log("Orquestrador", "Tarefa " + tarefa.getId() + " distribuída para o Worker " + workerId);
                sincronizarEstadoComBackup(); // Notifica o backup sobre a mudança de estado.
            } catch (IOException e) {
                Logger.log("Orquestrador", "Falha ao enviar tarefa para o Worker " + workerId + ". Reagendando...");
                // Se a comunicação falhar, assume que o worker caiu e inicia o tratamento da falha.
                lidarComFalhaWorker(workerId);
            }
        }
    }
    
    /**
     * Método executado periodicamente para verificar se algum worker parou de enviar heartbeats.
     */
    private void verificarWorkersAtivos() {
        long agora = System.currentTimeMillis();
        for (InfoWorker worker : workersAtivos.values()) {
            if (agora - worker.getUltimoHeartbeat() > TIMEOUT_HEARTBEAT_MS) {
                Logger.log("Orquestrador", "Worker " + worker.getId() + " não responde (timeout). Removendo.");
                lidarComFalhaWorker(worker.getId());
            }
        }
    }
    
    /**
     * Rotina para lidar com a falha de um worker.
     * Remove o worker da lista de ativos e redistribui as tarefas que estavam com ele.
     * Este método é sincronizado para garantir consistência.
     * @param workerId O ID do worker que falhou.
     */
    private synchronized void lidarComFalhaWorker(String workerId) {
        InfoWorker workerRemovido = workersAtivos.remove(workerId);
        if (workerRemovido != null) {
            workerIdsOrdenados.remove(workerId);
            
            // *** CORREÇÃO DO BUG ***
            // Se a lista de workers não estiver vazia, ajusta o índice para garantir que ele seja válido.
            if (!workerIdsOrdenados.isEmpty()) {
                proximoWorkerIndex = proximoWorkerIndex % workerIdsOrdenados.size();
            } else {
                proximoWorkerIndex = 0; // Se a lista ficar vazia, reseta para 0.
            }
            // *** FIM DA CORREÇÃO ***
            
            try {
                workerRemovido.getSocket().close(); // Fecha a conexão.
            } catch (IOException e) { /* Ignorar */ }
            
            Logger.log("Orquestrador", "Redistribuindo tarefas do worker " + workerId);
            // Itera sobre todas as tarefas para encontrar as que estavam com o worker falho.
            for (Tarefa tarefa : tarefas.values()) {
                if (workerId.equals(tarefa.getIdWorker()) && tarefa.getStatus() == Tarefa.StatusTarefa.EXECUTANDO) {
                    Logger.log("Orquestrador", "Reagendando tarefa " + tarefa.getId());
                    // Marca a tarefa como aguardando para ser reatribuída.
                    tarefa.setStatus(Tarefa.StatusTarefa.AGUARDANDO);
                    tarefa.setIdWorker(null);
                    distribuirTarefa(tarefa); // Tenta redistribuir imediatamente.
                }
            }
            sincronizarEstadoComBackup(); // Atualiza o backup sobre a remoção do worker e o estado das tarefas.
        }
    }

    /**
     * Envia o estado global atual (tarefas, workers, relógio) para o orquestrador de backup.
     * Sincronizado para garantir que o estado enviado seja consistente.
     */
    private synchronized void sincronizarEstadoComBackup() {
        if (saidaBackup == null) return; // Não faz nada se o backup não estiver conectado.
        
        try {
            // Cria um objeto de estado contendo apenas os dados serializáveis.
            // Isso evita tentar serializar objetos como Sockets.
            Map<String, Tarefa> estadoTarefas = new HashMap<>(tarefas);
            Set<String> estadoWorkers = new HashSet<>(workersAtivos.keySet());
            long tempoAtual = relogio.getTempo();

            Map<String, Object> estadoGlobal = new HashMap<>();
            estadoGlobal.put("tarefas", estadoTarefas);
            estadoGlobal.put("workers", estadoWorkers);
            estadoGlobal.put("relogio", tempoAtual);

            Mensagem msgSinc = new Mensagem(Mensagem.TipoMensagem.SINCRONIZAR_ESTADO, estadoGlobal);
            saidaBackup.writeObject(msgSinc);
            saidaBackup.flush();
            saidaBackup.reset(); // Limpa o cache de objetos do ObjectOutputStream para garantir que o estado seja reenviado.
            Logger.log("Orquestrador", "Estado sincronizado com o backup. Relógio: " + tempoAtual);

        } catch (IOException e) {
            Logger.log("Orquestrador", "Falha ao sincronizar com o backup: " + e.getMessage());
            // Se a sincronização falhar, tenta se reconectar.
            saidaBackup = null;
            conectarAoBackup();
        }
    }

    /**
     * Classe interna estática para armazenar informações sobre um worker conectado.
     */
    private static class InfoWorker {
        private final String id;
        private final Socket socket;
        private final ObjectOutputStream saida;
        private long ultimoHeartbeat;

        InfoWorker(String id, Socket socket, ObjectOutputStream saida) {
            this.id = id;
            this.socket = socket;
            this.saida = saida;
            this.ultimoHeartbeat = System.currentTimeMillis();
        }
        
        public String getId() { return id; }
        public Socket getSocket() { return socket; }
        public ObjectOutputStream getSaida() { return saida; }
        public long getUltimoHeartbeat() { return ultimoHeartbeat; }
        public void setUltimoHeartbeat(long ultimoHeartbeat) { this.ultimoHeartbeat = ultimoHeartbeat; }
    }
    
    /**
     * Classe interna que implementa a lógica de comunicação com um único cliente.
     */
    private class ManipuladorCliente implements Runnable {
        private final Socket socket;

        public ManipuladorCliente(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (
                ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())
            ) {
                String clienteToken = null;

                // Loop de leitura de mensagens do cliente.
                while (true) {
                    Mensagem msgRecebida = (Mensagem) entrada.readObject();
                    relogio.atualizar(msgRecebida.getTimestampLamport());
                    Logger.log("Orquestrador", "Mensagem recebida do cliente: " + msgRecebida.getTipo() + " | Relógio: " + relogio.getTempo());

                    // A primeira mensagem deve ser de autenticação.
                    if (msgRecebida.getTipo() == Mensagem.TipoMensagem.AUTENTICAR) {
                        Credenciais creds = (Credenciais) msgRecebida.getConteudo();
                        if (credenciaisUsuarios.getOrDefault(creds.getUsuario(), "").equals(creds.getSenha())) {
                            clienteToken = UUID.randomUUID().toString();
                            usuariosAutenticados.put(clienteToken, creds.getUsuario());
                            
                            saida.writeObject(new Mensagem(Mensagem.TipoMensagem.AUTENTICACAO_OK, clienteToken));
                            Logger.log("Orquestrador", "Cliente " + creds.getUsuario() + " autenticado com sucesso.");
                        } else {
                            saida.writeObject(new Mensagem(Mensagem.TipoMensagem.AUTENTICACAO_FALHOU, null));
                            Logger.log("Orquestrador", "Falha na autenticação para o usuário " + creds.getUsuario());
                            break;
                        }
                    // Valida o token para todas as outras operações.
                    } else if (clienteToken == null || !usuariosAutenticados.containsKey(msgRecebida.getToken())) {
                        Logger.log("Orquestrador", "Cliente não autenticado tentou realizar uma operação. Encerrando conexão.");
                        break;
                    } else {
                        // Processa a mensagem com base no seu tipo.
                        switch (msgRecebida.getTipo()) {
                            case SUBMETER_TAREFA:
                                Tarefa novaTarefa = (Tarefa) msgRecebida.getConteudo();
                                tarefas.put(novaTarefa.getId(), novaTarefa);
                                Logger.log("Orquestrador", "Tarefa " + novaTarefa.getId() + " recebida do cliente " + novaTarefa.getIdCliente());
                                distribuirTarefa(novaTarefa);
                                saida.writeObject(new Mensagem(Mensagem.TipoMensagem.TAREFA_RECEBIDA, novaTarefa.getId()));
                                break;
                            case CONSULTAR_STATUS_TAREFA:
                                String idTarefa = (String) msgRecebida.getConteudo();
                                Tarefa tarefaConsultada = tarefas.get(idTarefa);
                                saida.writeObject(new Mensagem(Mensagem.TipoMensagem.STATUS_TAREFA, tarefaConsultada));
                                break;
                            default:
                                Logger.log("Orquestrador", "Tipo de mensagem de cliente desconhecida: " + msgRecebida.getTipo());
                        }
                    }
                    saida.flush();
                }
            } catch (IOException | ClassNotFoundException e) {
                Logger.log("Orquestrador", "Conexão com cliente perdida: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) { /* Ignorar */ }
            }
        }
    }
    
    /**
     * Classe interna que implementa a lógica de comunicação com um único worker.
     */
    private class ManipuladorWorker implements Runnable {
        private final Socket socket;
        private String workerId;

        public ManipuladorWorker(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (
                ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())
            ) {
                while (true) {
                    Mensagem msg = (Mensagem) entrada.readObject();
                    relogio.tick(); // Evento interno de recebimento.

                    switch (msg.getTipo()) {
                        case REGISTRAR_WORKER:
                            this.workerId = (String) msg.getConteudo();
                            InfoWorker novoWorker = new InfoWorker(workerId, socket, saida);
                            workersAtivos.put(workerId, novoWorker);
                            workerIdsOrdenados.add(workerId);
                            Logger.log("Orquestrador", "Worker " + workerId + " registrado. Total de workers: " + workersAtivos.size());
                            sincronizarEstadoComBackup();

                            // Ao entrar um novo worker, tenta distribuir tarefas que estavam em espera.
                            tarefas.values().stream()
                                   .filter(t -> t.getStatus() == Tarefa.StatusTarefa.AGUARDANDO)
                                   .forEach(Orquestrador.this::distribuirTarefa);
                            break;
                        case HEARTBEAT:
                            if (workerId != null) {
                                InfoWorker worker = workersAtivos.get(workerId);
                                if (worker != null) {
                                    worker.setUltimoHeartbeat(System.currentTimeMillis());
                                }
                            }
                            break;
                        case TAREFA_CONCLUIDA:
                            Tarefa tarefaConcluida = (Tarefa) msg.getConteudo();
                            relogio.atualizar(tarefaConcluida.getTimestampLamport());

                            tarefas.computeIfPresent(tarefaConcluida.getId(), (id, t) -> {
                                t.setStatus(Tarefa.StatusTarefa.CONCLUIDA);
                                t.setTimestampLamport(relogio.getTempo());
                                return t;
                            });
                            Logger.log("Orquestrador", "Tarefa " + tarefaConcluida.getId() + " concluída pelo worker " + tarefaConcluida.getIdWorker() + " | Relógio: " + relogio.getTempo());
                            sincronizarEstadoComBackup();
                            break;
                        default:
                            Logger.log("Orquestrador", "Mensagem de worker desconhecida: " + msg.getTipo());
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                Logger.log("Orquestrador", "Conexão com worker " + workerId + " perdida: " + e.getMessage());
                // Se a conexão for perdida, trata como uma falha do worker.
                if (workerId != null) {
                    lidarComFalhaWorker(workerId);
                }
            }
        }
    }
}

