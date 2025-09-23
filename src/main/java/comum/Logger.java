package main.java.comum;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Classe utilitária simples para registrar logs formatados no console.
 * Ajuda a padronizar a saída de logs de todos os componentes do sistema.
 */
public class Logger {
    // Formato para o timestamp dos logs.
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Imprime uma mensagem de log formatada no console.
     * @param role O componente que está gerando o log (ex: "Orquestrador", "Worker-123").
     * @param message A mensagem a ser registrada.
     */
    public static void log(String role, String message) {
        String timestamp = dtf.format(LocalDateTime.now());
        // Formata a saída para incluir timestamp, o papel (role) e a mensagem.
        System.out.printf("[%s] [%s] %s%n", timestamp, role.toUpperCase(), message);
    }
}

