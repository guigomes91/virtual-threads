package caramelo.dev.virtualthread.travel;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.StructuredTaskScope;
import java.util.logging.Logger;
import java.util.stream.Stream;

// 1. Definição dos Modelos de Dados (Records)
sealed interface Travel permits RidesharingOffer, PublicTransportOffer {}

record RidesharingOffer(String company, double price) implements Travel {}

record PublicTransportOffer(String transport, LocalTime goTime) implements Travel {}

record TravelOffer(RidesharingOffer ridesharing, PublicTransportOffer publicTransport) {}

// 2. Escopo Customizado para Transporte Público
class PublicTransportScope extends StructuredTaskScope<List<PublicTransportOffer>> {
    private final List<List<PublicTransportOffer>> results = new CopyOnWriteArrayList<>();
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    @Override
    protected void handleComplete(Subtask<? extends List<PublicTransportOffer>> subtask) {
        switch (subtask.state()) {
            case SUCCESS -> results.add(subtask.get());
            case FAILED -> exceptions.add(subtask.exception());
            case UNAVAILABLE -> throw new IllegalStateException("Subtask ainda rodando...");
        }
    }

    public PublicTransportOffer bestOffer() {
        super.ensureOwnerAndJoined(); // Garante que o join() foi chamado
        return results.stream()
                .flatMap(List::stream)
                .min(Comparator.comparing(PublicTransportOffer::goTime))
                .orElseThrow(() -> new RuntimeException("Nenhuma oferta de transporte público"));
    }
}

public class TravelApp {

    private static final Logger logger = Logger.getLogger(TravelApp.class.getName());

    static void main(String[] args) throws Exception {
        logger.info("Iniciando busca de ofertas para o usuário...");
        TravelOffer finalOffer = fetchTravelOffers("Rua A", "Rua B");
        logger.info("Melhor oferta encontrada: " + finalOffer);
    }

    public static TravelOffer fetchTravelOffers(String loc, String dest) throws Exception {
        // Uso do StructuredTaskScope para rodar os dois serviços em paralelo
        try (var scope = new StructuredTaskScope<Travel>()) {

            // Dispara a busca de Ridesharing e Transporte Público simultaneamente
            StructuredTaskScope.Subtask<RidesharingOffer> ridesharingTask = scope.fork(() -> fetchCheapestRidesharing(loc, dest));
            StructuredTaskScope.Subtask<PublicTransportOffer> publicTask = scope.fork(() -> fetchEarliestPublicTransport(loc, dest));

            scope.join(); // Aguarda ambos os serviços terminarem

            return new TravelOffer(ridesharingTask.get(), publicTask.get());
        }
    }

    // Simula consulta a múltiplos servidores de Carro (Ridesharing)
    private static RidesharingOffer fetchCheapestRidesharing(String loc, String dest) throws Exception {
        try (var scope = new StructuredTaskScope<RidesharingOffer>()) {
            var sub1 = scope.fork(() -> { Thread.sleep(100); return new RidesharingOffer("Uber", 25.50); });
            var sub2 = scope.fork(() -> { Thread.sleep(150); return new RidesharingOffer("99", 22.10); });

            scope.join();

            return Stream.of(sub1, sub2)
                    .filter(s -> s.state() == StructuredTaskScope.Subtask.State.SUCCESS)
                    .map(StructuredTaskScope.Subtask::get)
                    .min(Comparator.comparingDouble(RidesharingOffer::price))
                    .orElseThrow();
        }
    }

    // Simula consulta via escopo customizado para Ônibus/Trem
    private static PublicTransportOffer fetchEarliestPublicTransport(String loc, String dest) throws Exception {
        try (var scope = new PublicTransportScope()) {
            scope.fork(() -> { Thread.sleep(120); return List.of(new PublicTransportOffer("Ônibus", LocalTime.of(10, 30))); });
            scope.fork(() -> { Thread.sleep(80);  return List.of(new PublicTransportOffer("Metrô", LocalTime.of(10, 15))); });

            scope.join();
            return scope.bestOffer();
        }
    }
}