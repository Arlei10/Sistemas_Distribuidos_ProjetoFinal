package main.java.modelo;

import java.io.Serializable;

/**
 * Representa uma unidade de trabalho (tarefa) a ser processada pelos workers.
 * Esta classe é serializável para permitir que seja enviada através da rede do orquestrador para os workers.
 */
public class Tarefa implements Serializable {
    private static final long serialVersionUID = 1L; // Garante a compatibilidade da serialização

    private final String id;            // Identificador único da tarefa.
    private final String idCliente;     // Identificador do cliente que submeteu a tarefa.
    private final String dados;         // Carga útil da tarefa (ex: dados a serem processados).
    private StatusTarefa status;        // O estado atual da tarefa no seu ciclo de vida.
    private String idWorker;            // ID do worker que está executando ou executou a tarefa.
    private long timestampLamport;      // Timestamp lógico para ordenação de eventos relacionados à tarefa.

    /**
     * Enum que define os possíveis estados de uma tarefa.
     */
    public enum StatusTarefa {
        AGUARDANDO, // A tarefa foi recebida, mas aguarda um worker disponível.
        EXECUTANDO, // A tarefa foi atribuída a um worker e está em processamento.
        CONCLUIDA,  // A tarefa foi processada com sucesso.
        FALHOU      // A tarefa falhou durante a execução (ou o worker falhou).
    }

    /**
     * Construtor da Tarefa.
     * @param id O ID único da tarefa.
     * @param idCliente O ID do cliente que a criou.
     * @param dados Os dados a serem processados.
     */
    public Tarefa(String id, String idCliente, String dados) {
        this.id = id;
        this.idCliente = idCliente;
        this.dados = dados;
        this.status = StatusTarefa.AGUARDANDO; // Uma nova tarefa sempre começa aguardando.
    }

    // --- Getters ---
    public String getId() {
        return id;
    }

    public String getIdCliente() {
        return idCliente;
    }

    public String getDados() {
        return dados;
    }

    public StatusTarefa getStatus() {
        return status;
    }

    public String getIdWorker() {
        return idWorker;
    }

    public long getTimestampLamport() {
        return timestampLamport;
    }

    // --- Setters ---
    public void setStatus(StatusTarefa status) {
        this.status = status;
    }

    public void setIdWorker(String idWorker) {
        this.idWorker = idWorker;
    }

    public void setTimestampLamport(long timestampLamport) {
        this.timestampLamport = timestampLamport;
    }

    /**
     * Representação textual do objeto Tarefa, útil para logs e debug.
     */
    @Override
    public String toString() {
        return "Tarefa{" +
               "id='" + id + '\'' +
               ", idCliente='" + idCliente + '\'' +
               ", status=" + status +
               ", idWorker='" + idWorker + '\'' +
               ", timestamp=" + timestampLamport +
               '}';
    }
}

