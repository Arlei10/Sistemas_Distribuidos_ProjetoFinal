package main.java.cliente;

import main.java.comum.RelogioLamport;
import main.java.modelo.Credenciais;
import main.java.modelo.Mensagem;
import main.java.modelo.Tarefa;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.UUID;

/**
 * Implementa a interface de linha de comando para o cliente.
 * Permite que um usuário se autentique, submeta tarefas e consulte o status de suas tarefas.
 */
public class Cliente {
    private final String ipOrquestrador;
    private final int portaOrquestrador;
    private ObjectOutputStream saida;   // Stream de saída para o orquestrador.
    private ObjectInputStream entrada;  // Stream de entrada do orquestrador.
    private String token;               // Token recebido após a autenticação.
    private String usuario;             // Nome do usuário logado.
    private final RelogioLamport relogio = new RelogioLamport(); // Relógio de Lamport do cliente.

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java cliente.Cliente <ip_orquestrador> <porta_orquestrador>");
            return;
        }
        String ip = args[0];
        int porta = Integer.parseInt(args[1]);
        new Cliente(ip, porta).iniciar();
    }

    public Cliente(String ipOrquestrador, int portaOrquestrador) {
        this.ipOrquestrador = ipOrquestrador;
        this.portaOrquestrador = portaOrquestrador;
    }

    /**
     * Inicia a execução do cliente, conectando-se ao orquestrador e exibindo o menu de interação.
     */
    private void iniciar() {
        try {
            // Conecta-se ao orquestrador.
            Socket socket = new Socket(ipOrquestrador, portaOrquestrador);
            this.saida = new ObjectOutputStream(socket.getOutputStream());
            this.entrada = new ObjectInputStream(socket.getInputStream());

            Scanner scanner = new Scanner(System.in);

            // Passo 1: Autenticação.
            if (!autenticar(scanner)) {
                System.out.println("Autenticação falhou. Encerrando.");
                return;
            }

            System.out.println("Autenticação bem-sucedida! Bem-vindo, " + this.usuario + ".");

            // Passo 2: Loop do menu principal.
            boolean executando = true;
            while (executando) {
                System.out.println("\nEscolha uma opção:");
                System.out.println("1. Submeter nova tarefa");
                System.out.println("2. Consultar status de uma tarefa");
                System.out.println("3. Sair");
                System.out.print("> ");

                int escolha = scanner.nextInt();
                scanner.nextLine(); // Consome o caractere de nova linha.

                switch (escolha) {
                    case 1:
                        submeterTarefa(scanner);
                        break;
                    case 2:
                        consultarStatus(scanner);
                        break;
                    case 3:
                        executando = false;
                        break;
                    default:
                        System.out.println("Opção inválida.");
                }
            }

            socket.close();

        } catch (IOException e) {
            System.err.println("Erro de conexão com o orquestrador: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Erro: dados inválidos recebidos do servidor.");
        }
    }
    
    /**
     * Lida com o processo de autenticação.
     * @param scanner Scanner para ler a entrada do usuário.
     * @return true se a autenticação for bem-sucedida, false caso contrário.
     */
    private boolean autenticar(Scanner scanner) throws IOException, ClassNotFoundException {
        System.out.print("Usuário: ");
        this.usuario = scanner.nextLine();
        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        Credenciais creds = new Credenciais(this.usuario, senha);
        enviarMensagem(new Mensagem(Mensagem.TipoMensagem.AUTENTICAR, creds));
        
        // Aguarda a resposta do orquestrador.
        Mensagem resposta = (Mensagem) entrada.readObject();
        relogio.atualizar(resposta.getTimestampLamport());

        if (resposta.getTipo() == Mensagem.TipoMensagem.AUTENTICACAO_OK) {
            this.token = (String) resposta.getConteudo();
            return true;
        }
        return false;
    }

    /**
     * Coleta dados do usuário para criar e submeter uma nova tarefa.
     * @param scanner Scanner para ler a entrada do usuário.
     */
    private void submeterTarefa(Scanner scanner) throws IOException, ClassNotFoundException {
        System.out.print("Descreva a carga da tarefa (ex: 'processar video 4k'): ");
        String dados = scanner.nextLine();
        
        String idTarefa = "Tarefa-" + UUID.randomUUID().toString().substring(0, 8);
        Tarefa novaTarefa = new Tarefa(idTarefa, this.usuario, dados);

        enviarMensagem(new Mensagem(Mensagem.TipoMensagem.SUBMETER_TAREFA, novaTarefa));
        
        Mensagem resposta = (Mensagem) entrada.readObject();
        relogio.atualizar(resposta.getTimestampLamport());

        if (resposta.getTipo() == Mensagem.TipoMensagem.TAREFA_RECEBIDA) {
            System.out.println("Tarefa enviada com sucesso! ID da Tarefa: " + resposta.getConteudo());
        } else {
            System.out.println("Falha ao enviar tarefa.");
        }
    }

    /**
     * Coleta o ID de uma tarefa e solicita seu status ao orquestrador.
     * @param scanner Scanner para ler a entrada do usuário.
     */
    private void consultarStatus(Scanner scanner) throws IOException, ClassNotFoundException {
        System.out.print("Digite o ID da tarefa: ");
        String idTarefa = scanner.nextLine();

        enviarMensagem(new Mensagem(Mensagem.TipoMensagem.CONSULTAR_STATUS_TAREFA, idTarefa));

        Mensagem resposta = (Mensagem) entrada.readObject();
        relogio.atualizar(resposta.getTimestampLamport());

        if (resposta.getTipo() == Mensagem.TipoMensagem.STATUS_TAREFA && resposta.getConteudo() != null) {
            Tarefa tarefa = (Tarefa) resposta.getConteudo();
            System.out.println("\n--- Status da Tarefa ---");
            System.out.println("ID: " + tarefa.getId());
            System.out.println("Status: " + tarefa.getStatus());
            System.out.println("Worker: " + (tarefa.getIdWorker() != null ? tarefa.getIdWorker() : "N/A"));
            System.out.println("Timestamp (Lamport): " + tarefa.getTimestampLamport());
            System.out.println("------------------------");
        } else {
            System.out.println("Tarefa não encontrada ou ocorreu um erro.");
        }
    }
    
    /**
     * Método auxiliar para enviar uma mensagem ao orquestrador.
     * Ele anexa o token e o timestamp de Lamport a cada mensagem.
     * @param msg A mensagem a ser enviada.
     */
    private void enviarMensagem(Mensagem msg) throws IOException {
        msg.setToken(this.token); // Anexa o token de autenticação.
        msg.setTimestampLamport(relogio.tick()); // Incrementa o relógio local e anexa o timestamp.
        saida.writeObject(msg);
        saida.flush();
    }
}

