package caramelo.dev.virtualthread;

import caramelo.dev.virtualthread.pinning.VirtualThreadWithSynchronized;
import caramelo.dev.virtualthread.reentrant.VirtualThreadWithReentrantLock;

public class Main {
    public static void main(String[] args) {
        // Obtém o valor da propriedade -Dreentrant passada via VM options
        // Boolean.getBoolean retorna true se a propriedade existir e for igual a "true"
        boolean useReentrant = Boolean.getBoolean("reentrant");

        long start = System.currentTimeMillis();
        if (useReentrant) {
            System.out.println("--- Iniciando Simulação com ReentrantLock (Sem Pinning) ---");
            VirtualThreadWithReentrantLock.simulateSolution();
        } else {
            System.out.println("--- Iniciando Simulação com synchronized (Com Pinning) ---");
            VirtualThreadWithSynchronized.simulatePinning();
        }

        long end = System.currentTimeMillis();
        System.out.println("Tempo final para " + (useReentrant ? "Reentrant: " : "Syncronized: ") + (end - start) + "ms");
    }
}