# Java Moderno: Concorrência com Threads Virtuais (JDK 21)

Este projeto demonstra a aplicação prática de Threads Virtuais e técnicas avançadas de concorrência introduzidas no Java 21, focando em escalabilidade e eficiência de recursos
. Os exemplos são baseados nos problemas resolvidos do livro Java Coding Problems, Second Edition.

🚀 Tecnologias Utilizadas

- Java 21 (LTS)
- Virtual Threads (Project Loom)
- ReentrantLock para gerenciamento de travas sem pinning

---
🛠️ Exemplos Implementados
1. Evitando Pinning com ReentrantLock
   - Este exemplo resolve o Problema 236 do livro: como evitar que uma thread virtual fique "estacionada" (pinned) em sua thread portadora (carrier thread)
   
   - O Problema: O uso de blocos synchronized impede que a thread virtual seja desmontada da carrier thread durante operações de bloqueio (como Thread.sleep ou I/O), limitando o throughput do sistema
   
   - A Solução: Substituir o synchronized por ReentrantLock. Isso permite que a JVM realize o unmounting da thread virtual com sucesso, liberando a carrier thread para processar outras tarefas
   
   Exemplo de Código:
   ```java
   // Versão que evita o Pinning
   Lock lock = new ReentrantLock();
   executor.submit(() -> {
        lock.lock();
        try {
            Thread.sleep(1000); // Thread virtual é desmontada com sucesso
            logger.info("Executado: " + Thread.currentThread());
        } finally {
            lock.unlock();
        }
   });
   ``` 

---
🚦 Como Executar

O projeto permite alternar entre as implementações de simulação via parâmetros de sistema.

- Rodar Simulação de Pinning vs Solution
- Para testar a diferença de workers entre o uso de synchronized e ReentrantLock:
- Para usar a solução com ReentrantLock (Sem Pinning):
- Para usar a versão com Synchronized (Com Pinning):

Dica: A flag -Djdk.tracePinnedThreads=short imprimirá no console o stack trace sempre que ocorrer um pinning no sistema

---

📚 Referências

- Livro: Java Coding Problems, Second Edition (Anghel Leonard)
- JEP 444: Virtual Threads
- JEP 453: Structured Concurrency (Preview)

--------------------------------------------------------------------------------
Este projeto foi desenvolvido para fins de estudo sobre a evolução da concorrência na plataforma Java