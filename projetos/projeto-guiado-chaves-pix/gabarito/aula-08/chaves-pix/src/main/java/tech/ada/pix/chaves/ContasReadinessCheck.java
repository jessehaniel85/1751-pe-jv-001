package tech.ada.pix.chaves;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Aula 5: o chaves-pix só está pronto para cadastrar se o serviço de contas estiver pronto.
 * Ponto para discutir em aula: com contas fora, o GET de chaves ainda funcionaria,
 * mas o orquestrador tira a instância inteira do balanceamento. Vale a pena?
 */
@Readiness
@ApplicationScoped
public class ContasReadinessCheck implements HealthCheck {

    @Inject
    @RestClient
    ContasClient client;

    @Override
    public HealthCheckResponse call() {
        try {
            client.prontidao();
            return HealthCheckResponse.up("servico-de-contas");
        } catch (RuntimeException e) {
            return HealthCheckResponse.named("servico-de-contas").withData("erro", String.valueOf(e.getMessage())).down().build();
        }
    }
}
