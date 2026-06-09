package caramelo.dev.virtualthread.testteam;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.logging.Logger;

// 1. Definição da Record para a Equipe
record TestingTeam(String team1, String team2, String team3) {}

public class CompletableFutureVirtualThreads {
    private static final Logger logger = Logger.getLogger(CompletableFutureVirtualThreads.class.getName());

    // 2. Executor que cria uma nova Thread Virtual por tarefa
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    static void main(String[] args) throws InterruptedException, ExecutionException {
        TestingTeam team = buildTestingTeam();
        logger.info("Equipe montada: " + team);
    }

    public static TestingTeam buildTestingTeam() throws InterruptedException, ExecutionException {
        // 3. Disparo das tarefas assíncronas vinculadas ao executor de threads virtuais
        CompletableFuture<String> cfTester1 = fetchTesterAsync(1);
        CompletableFuture<String> cfTester2 = fetchTesterAsync(2);
        CompletableFuture<String> cfTester3 = fetchTesterAsync(3);

        // 4. Coordenação: aguarda a conclusão de todos os futuros
        CompletableFuture<Void> allTesters = CompletableFuture.allOf(cfTester1, cfTester2, cfTester3);

        // Bloqueia a thread principal até que as virtuais terminem
        allTesters.get();

        // 5. Extração imediata dos resultados usando resultNow() (JDK 19+)
        return new TestingTeam(
                cfTester1.resultNow(),
                cfTester2.resultNow(),
                cfTester3.resultNow()
        );
    }

    private static CompletableFuture<String> fetchTesterAsync(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Log mostra que a tarefa roda em uma VirtualThread
                logger.info("Buscando testador " + id + " em: " + Thread.currentThread());
                return fetchTester(id);
            } catch (IOException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    // Simulador de chamada de servidor usando HttpClient
    public static String fetchTester(int id) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("https://reqres.in/api/users/" + id))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }

            throw new RuntimeException("Erro ao buscar: " + response.statusCode());
        }
    }
}