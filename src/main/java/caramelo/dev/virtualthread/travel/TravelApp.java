package caramelo.dev.virtualthread.travel;

import java.time.LocalTime;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Stream;

// 1. Modelagem de Dados com Sealed Interfaces e Records
sealed interface Travel permits RidesharingOffer, PublicTransportOffer {}

record RidesharingOffer(String company, double price) implements Travel {}
record PublicTransportOffer(String transport, LocalTime goTime) implements Travel {}
record TravelOffer(RidesharingOffer rides, PublicTransportOffer publicTransport) {}

// 2. Escopo Customizado para coletar diferentes tipos de resultados
class TravelScope extends StructuredTaskScope<Travel> {
    private volatile RidesharingOffer ridesharingOffer;
    private volatile PublicTransportOffer publicTransportOffer;

    @Override
    protected void handleComplete(Subtask<? extends Travel> subtask) {
        // Analisa o estado e o tipo do resultado de cada thread filha
        if (subtask.state() == Subtask.State.SUCCESS) {
            Travel result = subtask.get();
            if (result instanceof RidesharingOffer ro) this.ridesharingOffer = ro;
            else if (result instanceof PublicTransportOffer pto) this.publicTransportOffer = pto;
        }
    }

    public TravelOffer result() {
        super.ensureOwnerAndJoined(); // Garante que join() foi chamado
        return new TravelOffer(ridesharingOffer, publicTransportOffer);
    }
}

public class TravelApp {
    // 3. Scoped Values para contexto imutável (substitui ThreadLocal)
    public static final ScopedValue<String> USER = ScopedValue.newInstance();
    public static final ScopedValue<String> ORIGIN = ScopedValue.newInstance();

    static void main(String[] args) throws Exception {
        // Vinculando o usuário no nível da aplicação
        ScopedValue.where(USER, "Guilherme Gomes").run(() -> {
            try {
                System.out.println("Buscando ofertas para: " + USER.get());
                TravelOffer offer = fetchOffers("Centro", "Aeroporto");
                System.out.println("Resultado Final: " + offer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static TravelOffer fetchOffers(String loc, String dest) throws Exception {
        // 4. Combinando ScopedValue, Scope e Streams
        return ScopedValue.where(ORIGIN, loc).call(() -> {
            try (var scope = new TravelScope()) {

                // Disparando sub-tarefas concorrentes usando Streams
                Stream.of(1, 2)
                        .<Callable<Travel>>map(id -> () -> id == 1 ? getCarOffer() : getTrainOffer())
                        .forEach(scope::fork);

                // Espera todas terminarem (ou timeout)
                scope.join();

                return scope.result();
            }
        });
    }

    // Simuladores de Serviços Externos
    private static RidesharingOffer getCarOffer() {
        System.out.println("Consultando Carros saindo de: " + ORIGIN.get());
        return new RidesharingOffer("Uber", 45.50);
    }

    private static PublicTransportOffer getTrainOffer() {
        return new PublicTransportOffer("Metrô", LocalTime.of(15, 30));
    }
}