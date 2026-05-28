package caramelo.dev.virtualthread.travel;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.logging.Logger;
import java.util.stream.Stream;

// --- 1. MODELOS DE DADOS (RECORDS) ---
sealed interface Travel permits RidesharingOffer, PublicTransportOffer {}

record RidesharingOffer(String company, double price) implements Travel {}

record PublicTransportOffer(String transport, LocalTime goTime) implements Travel {}

record TravelOffer(RidesharingOffer ridesharing, PublicTransportOffer publicTransport) {}

// --- 2. ESCOPOS CUSTOMIZADOS ---

// Escopo para Transporte Público: Coleta listas e busca a que sai mais cedo
class PublicTransportScope extends StructuredTaskScope<List<PublicTransportOffer>> {
    private final List<List<PublicTransportOffer>> results = new CopyOnWriteArrayList<>();

    @Override
    protected void handleComplete(Subtask<? extends List<PublicTransportOffer>> subtask) {
        if (subtask.state() == Subtask.State.SUCCESS) {
            results.add(subtask.get());
        }
    }

    public PublicTransportOffer recommendedPublicTransport() {
        super.ensureOwnerAndJoined(); // Garante que join() foi chama
        return results.stream()
                .flatMap(List::stream)
                .min(Comparator.comparing(PublicTransportOffer::goTime))
                .orElse(null);
    }
}

// Escopo de Viagem Principal: Gerencia os resultados dos sub-serviços
class TravelScope extends StructuredTaskScope<Travel> {
    private volatile RidesharingOffer ridesharingOffer;
    private volatile PublicTransportOffer publicTransportOffer;
    private volatile TimeoutException timeoutException;

    @Override
    protected void handleComplete(Subtask<? extends Travel> subtask) {
        switch (subtask.state()) {
            case SUCCESS -> {
                // Pattern Matching para Records
                if (subtask.get() instanceof RidesharingOffer ro) {
                    this.ridesharingOffer = ro;
                } else if (subtask.get() instanceof PublicTransportOffer pto) {
                    this.publicTransportOffer = pto;
                }
            }
            case FAILED -> {
                if (subtask.exception() instanceof TimeoutException te) {
                    this.timeoutException = te;
                }
            }
        }
    }

    public TravelOffer recommendedTravelOffer() {
        super.ensureOwnerAndJoined();
        if (timeoutException != null) {
            Logger.getLogger("TravelApp").warning("Atenção: Alguns serviços demoraram a responder (Timeout).");
        }
        return new TravelOffer(ridesharingOffer, publicTransportOffer);
    }
}

// --- 3. APLICAÇÃO PRINCIPAL ---
public class TravelApp {
    private static final Logger logger = Logger.getLogger(TravelApp.class.getName());

    // Scoped Values para contexto imutável e seguro
    public static final ScopedValue<String> USER = ScopedValue.newInstance();
    public static final ScopedValue<String> LOC = ScopedValue.newInstance();
    public static final ScopedValue<String> DEST = ScopedValue.newInstance();
    public static final ScopedValue<Double> CAR_ONE_DISCOUNT = ScopedValue.newInstance();
    public static final ScopedValue<Boolean> PUBLIC_TRANSPORT_TICKET = ScopedValue.newInstance();

    public static void main(String[] args) throws Exception {
        String user = "Caramelo_Java";
        String origin = "Centro";
        String destination = "Aeroporto";

        // Vinculando USER no nível da aplicação
        ScopedValue.where(USER, user).run(() -> {
            try {
                logger.info("Iniciando busca para o usuário: " + USER.get());
                TravelOffer offer = fetchTravelOffers(origin, destination);
                logger.info("Resultado Final: " + offer);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static TravelOffer fetchTravelOffers(String loc, String dest) throws Exception {
        // Vinculando Localização e Destino para o escopo de execução
        return ScopedValue.where(LOC, loc).where(DEST, dest).call(() -> {
            try (var scope = new TravelScope()) {

                // Fork 1: Ridesharing (apenas se logado)
                if (USER.isBound()) {
                    scope.fork(TravelApp::fetchRidesharingOffers);
                }

                // Fork 2: Transporte Público (com ticket especial para este fork)
                scope.fork(() -> ScopedValue.where(PUBLIC_TRANSPORT_TICKET, true)
                        .call(TravelApp::fetchPublicTransportOffers));

                scope.join();
                return scope.recommendedTravelOffer();
            }
        });
    }

    // --- 4. SERVIÇOS SIMULADOS ---
    private static RidesharingOffer fetchRidesharingOffers() throws Exception {
        // Uso de StructuredTaskScope com TIMEOUT de 50ms
        try (var scope = new StructuredTaskScope<RidesharingOffer>()) {
            var sub1 = scope.fork(() -> {
                // Simula consulta ao banco com ScopedValue
                double discount = CAR_ONE_DISCOUNT.orElse(0.0);
                return new RidesharingOffer("Uber", 25.0 - discount);
            });
            var sub2 = scope.fork(() -> new RidesharingOffer("99", 22.0));

            // Espera até 50ms ou lança TimeoutException
            scope.joinUntil(Instant.now().plusMillis(50));

            return Stream.of(sub1, sub2)
                    .filter(s -> s.state() == Subtask.State.SUCCESS)
                    .map(Subtask::get)
                    .min(Comparator.comparingDouble(RidesharingOffer::price))
                    .orElse(null);
        }
    }

    private static PublicTransportOffer fetchPublicTransportOffers() throws Exception {
        try (var scope = new PublicTransportScope()) {
            // Verifica ticket via ScopedValue
            if (PUBLIC_TRANSPORT_TICKET.orElse(false)) {
                scope.fork(() -> List.of(new PublicTransportOffer("Metrô", LocalTime.of(14, 30))));
                scope.fork(() -> List.of(new PublicTransportOffer("Ônibus", LocalTime.of(14, 45))));
            }
            scope.join();
            return scope.recommendedPublicTransport();
        }
    }
}