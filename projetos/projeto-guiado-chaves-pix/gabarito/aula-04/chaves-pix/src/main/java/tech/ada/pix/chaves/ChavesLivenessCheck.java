package tech.ada.pix.chaves;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Aula 4: liveness responde "o processo está vivo?". Não consulta banco nem outro serviço:
 * se falhasse por causa deles, o orquestrador reiniciaria um processo saudável.
 * A prontidão do banco (readiness) vem pronta do Agroal, sem código.
 */
@Liveness
@ApplicationScoped
public class ChavesLivenessCheck implements HealthCheck {

    @Inject
    PixConfig config;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("chaves-pix")
                .withData("limite-por-conta", config.chaves().limitePorConta())
                .up()
                .build();
    }
}
