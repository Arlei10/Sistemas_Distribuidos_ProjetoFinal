package main.java.modelo;

import java.io.Serializable;

/**
 * Representa um envelope de mensagem genérico para a comunicação entre os diferentes componentes do sistema (Cliente, Orquestrador, Worker).
 * Esta classe é serializável para que seus objetos possam ser convertidos em bytes e enviados pela rede.
 * Contém um tipo para identificar a ação desejada e um conteúdo (payload) com os dados necessários.
 */
public class Mensagem implements Serializable {
    // serialVersionUID é um identificador de versão para a classe serializável.
    // Ajuda a garantir que a mesma versão da classe está sendo usada durante a serialização e desserialização.
    private static final long serialVersionUID = 1L;

    // O tipo da mensagem, que define a intenção da comunicação.
    private final TipoMensagem tipo;
    // O conteúdo da mensagem (carga útil). Pode ser qualquer objeto, como uma Tarefa, Credenciais, etc.
    private final Object conteudo;
    // Token de autenticação usado para validar requisições de clientes já autenticados.
    private String token;
    // Timestamp do Relógio de Lamport para ajudar a estabelecer uma ordem causal de eventos no sistema distribuído.
    private long timestampLamport;

    /**
     * Enum que define todos os tipos de mensagens possíveis no sistema,
     * organizadas pela direção da comunicação (ex: Cliente para Orquestrador).
     */
    public enum TipoMensagem {
        // Fluxo: Cliente -> Orquestrador
        AUTENTICAR,               // Solicita a autenticação de um cliente.
        SUBMETER_TAREFA,          // Envia uma nova tarefa para ser processada.
        CONSULTAR_STATUS_TAREFA,  // Pede o status de uma tarefa específica.

        // Fluxo: Orquestrador -> Cliente
        AUTENTICACAO_OK,          // Resposta informando que a autenticação foi bem-sucedida.
        AUTENTICACAO_FALHOU,      // Resposta informando que a autenticação falhou.
        TAREFA_RECEBIDA,          // Confirmação de que a tarefa foi recebida pelo orquestrador.
        STATUS_TAREFA,            // Resposta com o estado atual de uma tarefa consultada.

        // Fluxo: Worker -> Orquestrador
        REGISTRAR_WORKER,         // Um novo worker se apresenta ao orquestrador.
        HEARTBEAT,                // Sinal de vida periódico enviado pelo worker para mostrar que está ativo.
        TAREFA_CONCLUIDA,         // Notificação de que uma tarefa foi finalizada com sucesso.

        // Fluxo: Orquestrador -> Worker
        NOVA_TAREFA,              // Envia uma nova tarefa para um worker específico executar.
        
        // Fluxo: Orquestrador -> Orquestrador Backup
        SINCRONIZAR_ESTADO        // Mensagem contendo o estado global para replicação no backup.
    }

    /**
     * Construtor da classe Mensagem.
     * @param tipo O tipo da mensagem, definido no enum TipoMensagem.
     * @param conteudo O objeto de dados a ser transportado.
     */
    public Mensagem(TipoMensagem tipo, Object conteudo) {
        this.tipo = tipo;
        this.conteudo = conteudo;
    }

    // --- Getters ---

    public TipoMensagem getTipo() {
        return tipo;
    }

    public Object getConteudo() {
        return conteudo;
    }
    
    public String getToken() {
        return token;
    }

    public long getTimestampLamport() {
        return timestampLamport;
    }

    // --- Setters ---

    public void setToken(String token) {
        this.token = token;
    }
    
    public void setTimestampLamport(long timestampLamport) {
        this.timestampLamport = timestampLamport;
    }
}

