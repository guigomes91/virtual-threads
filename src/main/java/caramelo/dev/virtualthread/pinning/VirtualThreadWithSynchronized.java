package caramelo.dev.virtualthread.pinning;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class VirtualThreadWithSynchronized {

    private static final Logger logger = Logger.getLogger(VirtualThreadWithSynchronized.class.getName());
    private static final int NUMBER_OF_TASKS = 25;

    public static void simulatePinning() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < NUMBER_OF_TASKS; i++) {
                executor.submit(() -> {
                    // O bloco synchronized causa o PINNING
                    synchronized (VirtualThreadWithSynchronized.class) {
                        try {
                            logger.info("Iniciando tarefa Pinned: " + Thread.currentThread());
                            Thread.sleep(1000); // Operação de bloqueio
                            logger.info("Finalizando tarefa Pinned: " + Thread.currentThread());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        }
    }
}