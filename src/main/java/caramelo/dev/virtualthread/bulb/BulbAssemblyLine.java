package caramelo.dev.virtualthread.bulb;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class BulbAssemblyLine {

    private static final Logger logger = Logger.getLogger(BulbAssemblyLine.class.getName());

    // Configurações do Cenário
    private static final int PRODUCERS = 3;
    private static final int INITIAL_CONSUMERS = 2;
    private static final int MAX_CONSUMERS = 50;
    private static final int MAX_QUEUE_SIZE = 5;

    private static final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private static final Semaphore consumerSemaphore = new Semaphore(INITIAL_CONSUMERS, true);
    private static final AtomicInteger currentConsumers = new AtomicInteger(INITIAL_CONSUMERS);
    private static final AtomicBoolean signalRemove = new AtomicBoolean(false);

    static void main(String[] args) throws InterruptedException {
        // 1. Iniciar Produtores (Checkers)
        for (int i = 0; i < PRODUCERS; i++) {
            Thread.ofVirtual().name("Producer-", i).start(BulbAssemblyLine::produceBulbs);
        }

        // 2. Iniciar Consumidores Iniciais (Packers)
        for (int i = 0; i < INITIAL_CONSUMERS; i++) {
            startConsumer();
        }

        // 3. Iniciar Monitor de Escalonamento Dinâmico
        monitorQueueSize();

        // Manter a aplicação rodando
        Thread.sleep(Duration.ofMinutes(5));
    }

    private static void produceBulbs() {
        int count = 0;
        while (true) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1000)); // Simula tempo de teste
                String bulb = "Bulb-" + count++;
                queue.put(bulb);
                logger.info("Checked: " + bulb + " | Queue size: " + queue.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            }
        }
    }

    private static void startConsumer() {
        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    // Verifica se deve diminuir o número de consumidores
                    if (signalRemove.get() && currentConsumers.get() > INITIAL_CONSUMERS) {
                        if (signalRemove.compareAndSet(true, false)) {
                            currentConsumers.decrementAndGet();
                            break; // Encerra esta thread virtual
                        }
                    }

                    consumerSemaphore.acquire();
                    String bulb = queue.poll(10, TimeUnit.SECONDS);
                    if (bulb != null) {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 10000)); // Empacotamento lento
                        logger.warning("Packed: " + bulb + " by " + Thread.currentThread());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    consumerSemaphore.release();
                }
            }
        });
    }

    private static void monitorQueueSize() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            int qSize = queue.size();

            // Lógica de Aumento
            if (qSize > MAX_QUEUE_SIZE && currentConsumers.get() < MAX_CONSUMERS) {
                logger.warning("### Adding a new consumer... Bulbs in queue: " + qSize);
                consumerSemaphore.release(); // Adiciona permissão para nova thread
                startConsumer();
                currentConsumers.incrementAndGet();
            }
            // Lógica de Diminuição
            else if (qSize == 0 && currentConsumers.get() > INITIAL_CONSUMERS) {
                logger.warning("### Queue empty. Signaling to remove a consumer...");
                signalRemove.set(true);
            }
        }, 5, 3, TimeUnit.SECONDS);
    }
}