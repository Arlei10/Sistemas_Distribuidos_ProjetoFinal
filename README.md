# Sistemas_Distribuidos_ProjetoFinal

Plataforma Distribuída de Orquestração de Tarefas
Este projeto é uma simulação de um sistema distribuído para processamento colaborativo, desenvolvido como parte da disciplina de Sistemas Distribuídos. A plataforma permite que clientes submetam tarefas, que são distribuídas de forma balanceada entre múltiplos nós de processamento (workers), enquanto o estado do sistema é monitorado e replicado para garantir a tolerância a falhas.

Funcionalidades Principais
- Autenticação de Clientes: Apenas clientes autenticados com usuário e senha podem interagir com o sistema.

- Submissão e Consulta de Tarefas: Clientes podem submeter novas tarefas e consultar o estado de tarefas existentes em tempo real.

- Balanceamento de Carga (Round Robin): As tarefas são distribuídas de forma cíclica e equitativa entre os workers ativos.

- Monitoramento de Workers (Heartbeat): Workers enviam "sinais de vida" periodicamente. Se um worker falhar, o sistema deteta a sua ausência.

- Replicação de Estado e Failover: Um Orquestrador Secundário (Backup) mantém uma cópia do estado do sistema. Se o Orquestrador Principal falhar, o backup pode assumir (failover semi-automático).

- Redistribuição de Tarefas: Se um worker falhar enquanto processa uma tarefa, essa tarefa é automaticamente reatribuída a outro worker disponível.

- Ordenação Causal de Eventos: Utilização de Relógios Lógicos de Lamport para atribuir um timestamp a cada evento, permitindo rastrear a causalidade.

Estrutura do Projeto
O código-fonte está localizado em src/main/java/ e está organizado nos seguintes pacotes:

- cliente/: Contém a lógica da aplicação do cliente, que interage com o utilizador.

- comum/: Classes utilitárias partilhadas por todos os componentes (ex: Logger, RelogioLamport).

- modelo/: Classes de dados (DTOs) que representam as entidades do sistema (ex: Tarefa, Mensagem).

- orquestrador/: Contém a lógica do Orquestrador Principal e do Secundário (Backup).

- worker/: Contém a lógica dos nós de processamento que executam as tarefas.

Arquitetura Visual
O diagrama abaixo ilustra a arquitetura de alto nível do sistema, mostrando a relação entre os clientes, os servidores de orquestração e os nós de processamento.

<img width="517" height="785" alt="image" src="https://github.com/user-attachments/assets/4df87df8-8885-4eed-8231-1d19c1754b26" />

Como Compilar e Executar
Pré-requisitos
Java Development Kit (JDK) - Versão 8 ou superior.

Passo 1: Compilação
Abra um terminal na pasta raiz do projeto (a pasta Sistemas_Distribuidos_ProjetoFinal).

Execute o seguinte comando para compilar todos os ficheiros .java. O -d . instrui o compilador a colocar os ficheiros .class na estrutura de pacotes correta a partir do diretório atual.

```java
javac -encoding UTF-8 -d . src/main/java/cliente/*.java src/main/java/comum/*.java src/main/java/modelo/*.java src/main/java/orquestrador/*.java src/main/java/worker/*.java
```
(Nota: O ** pode não funcionar em versões mais antigas do CMD. Recomenda-se o uso do PowerShell ou de um terminal Git Bash.)

Passo 2: Execução
É crucial iniciar os componentes na ordem correta, cada um no seu próprio terminal. Todos os comandos de execução devem ser executados a partir da pasta raiz do projeto.

1. Iniciar o Orquestrador de Backup (Terminal 1)
```java
java main.java.orquestrador.OrquestradorSecundario > backup.log
```
2. Iniciar o Orquestrador Principal (Terminal 2)
```java
java main.java.orquestrador.Orquestrador > principal.log
```
3. Iniciar os Workers (Terminais 3, 4, ...)
Abra um novo terminal para cada worker.

# Iniciar o Worker 1
```java
java main.java.worker.Worker localhost 5001 > worker1.log
```
# Iniciar o Worker 2 (em outro terminal)
```java
java main.java.worker.Worker localhost 5001 > worker2.log
```
4. Iniciar o Cliente (Terminal 5)
```java
java main.java.cliente.Cliente localhost 5000
```
Credenciais de teste:
```java
Usuário: cliente1, Senha: senha123

Usuário: cliente2, Senha: senha456
```
Como Testar a Tolerância a Falhas
Siga os passos de execução e inicie pelo menos dois workers.

No terminal do cliente, autentique-se e submeta uma nova tarefa.

Observe os logs do Orquestrador Principal para ver qual worker recebeu a tarefa.

Vá ao terminal do worker que está a processar a tarefa e pressione Ctrl+C para o encerrar abruptamente.

Observe os logs do Orquestrador Principal novamente. Você verá uma mensagem a indicar que o worker falhou, seguida de outra a indicar que a tarefa foi redistribuída para o outro worker ativo.

Como Limpar o Projeto
Para remover todos os ficheiros gerados (.class e .log), execute os seguintes comandos a partir da pasta raiz do projeto:

No PowerShell:

# Remover ficheiros compilados
```java
Get-ChildItem -Path "main" -Recurse -Include *.class | Remove-Item
```
# Remover ficheiros de log
```java
Remove-Item *.log -ErrorAction SilentlyContinue
```
No CMD:

# Remover ficheiros compilados
```java
del /s /q main\*.class
```
# Remov```er ficheiros de log
del *.log
