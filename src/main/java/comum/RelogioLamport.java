package main.java.comum;

/**
 * Implementação de um Relógio Lógico de Lamport.
 * Este relógio é usado para atribuir um timestamp a cada evento no sistema distribuído,
 * permitindo estabelecer uma ordem causal parcial entre eles.
 */
public class RelogioLamport {
    private long tempo;

    public RelogioLamport() {
        this.tempo = 0;
    }

    /**
     * Incrementa o tempo lógico local.
     * Deve ser chamado sempre que um processo executa um evento interno (ex: enviar uma mensagem).
     * @return O novo valor do tempo lógico.
     */
    public synchronized long tick() {
        tempo++;
        return tempo;
    }

    /**
     * Atualiza o tempo lógico local ao receber uma mensagem de outro processo.
     * O tempo local é ajustado para ser o máximo entre o tempo atual e o tempo recebido, mais um.
     * Isso garante a propriedade "aconteceu-antes".
     * @param tempoRecebido O timestamp de Lamport da mensagem recebida.
     */
    public synchronized void atualizar(long tempoRecebido) {
        tempo = Math.max(tempo, tempoRecebido) + 1;
    }

    /**
     * Retorna o valor atual do tempo lógico.
     * @return O tempo lógico atual.
     */
    public synchronized long getTempo() {
        return tempo;
    }
}

