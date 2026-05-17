package caramelo.dev.virtualthread.reentrant;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class VirtualThreadWithReentrantLock {

    private static final Logger logger = Logger.getLogger(VirtualThreadWithReentrantLock.class.getName());
    private static final int NUMBER_OF_TASKS = 25;
    private static final Lock lock = new ReentrantLock();

    public static void simulateSolution() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < NUMBER_OF_TASKS; i++) {
                executor.submit(() -> {
                    lock.lock(); // Substitui o synchronized
                    try {
                        logger.info("Iniciando tarefa Lock: " + Thread.currentThread());
                        Thread.sleep(1000); // Aqui a thread virtual DESMONTA com sucesso
                        logger.info("Finalizando tarefa Lock: " + Thread.currentThread());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        lock.unlock();
                    }
                });
            }
        }
    }
}