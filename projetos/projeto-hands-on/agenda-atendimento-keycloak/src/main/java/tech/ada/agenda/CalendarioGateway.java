package tech.ada.agenda;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class CalendarioGateway {

  private final CalendarioClient calendarioClient;

  public CalendarioGateway(@RestClient CalendarioClient calendarioClient) {
    this.calendarioClient = calendarioClient;
  }

  @Timeout(500)
  @Retry(maxRetries = 5)
  @CircuitBreaker(requestVolumeThreshold = 10)
  @Fallback(fallbackMethod = "calendarioIndisponivel")
  public DiaDoCalendario consultar(String agencia, LocalDate data) {
    return calendarioClient.consultar(agencia, data);
  }

  DiaDoCalendario calendarioIndisponivel(String agencia, LocalDate data) {
    throw new CalendarioIndisponivelException();
  }

}
