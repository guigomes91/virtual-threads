package caramelo.dev.virtualthread.stream;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MapMultiVirtualThreads {

    static void main(String[] args) throws InterruptedException {

        // 1. Criamos um executor que abre uma thread virtual por tarefa
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 2. Definimos um conjunto de tarefas simuladas
            List<Callable<String>> tasks = List.of(
                    () -> "Resultado A - Dados de contato do cliente Guilherme recuperado",
                    () -> { throw new RuntimeException("Erro na B - não foi possivel carregar o endereço"); }, // Simula falha
                    () -> "Resultado C - Dados da conta do cliente Guilherme recuperado"
            );

            // 3. Disparamos todas as tarefas
            List<Future<String>> futuresTask = executor.invokeAll(tasks);

            // 4. Usamos Stream com mapMulti para processar os resultados
            List<String> results = futuresTask.stream()
                    // Filtramos apenas as tarefas que tiveram sucesso
                    .filter(f -> f.state() == Future.State.SUCCESS)
                    // mapMulti extrai o valor de forma direta e eficiente
                    .<String>mapMulti((dadosClienteFuture, consumer) -> {
                        // resultNow() obtém o valor sem exceções checadas
                        consumer.accept(dadosClienteFuture.resultNow());
                    }).toList();

            System.out.println("Resultados processados: " + results);
        }
    }
}
