package tech.ada.pix.chaves;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Aula 5: ponto único de chamada ao serviço de contas, com as políticas de Fault Tolerance.
 * Ordem de aplicação (MicroProfile): Fallback envolve Retry, que envolve CircuitBreaker, que envolve Timeout.
 * Os valores também estão em application.properties (a propriedade vence a anotação).
 */
@ApplicationScoped
public class ContasGateway {

    @Inject
    @RestClient
    ContasClient client;

    @Timeout(500)
    @Retry(maxRetries = 2, abortOn = ContaInexistenteException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, skipOn = ContaInexistenteException.class)
    @Fallback(fallbackMethod = "contasIndisponivel", skipOn = ContaInexistenteException.class)
    public ContaResponse confirmarConta(String agencia, String numeroConta) {
        return client.buscar(agencia, numeroConta);
    }

    ContaResponse contasIndisponivel(String agencia, String numeroConta) {
        throw new ContasIndisponivelException();
    }
}
