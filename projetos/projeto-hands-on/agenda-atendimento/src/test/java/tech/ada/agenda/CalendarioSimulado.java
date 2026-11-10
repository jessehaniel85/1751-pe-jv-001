package tech.ada.agenda;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Aula 5: nos testes, o calendário de verdade (porta 8081) não está no ar. @Mock troca o cliente REST por esta classe
 * em todos os @QuarkusTest: todo dia é útil. Quem precisa de outro comportamento usa @InjectMock (CalendarioGatewayTest).
 */
@Mock
@ApplicationScoped
@RestClient
public class CalendarioSimulado implements CalendarioClient {

  @Override
  public DiaDoCalendario consultar(String agencia, LocalDate data) {
    return new DiaDoCalendario(data, true, null);
  }
}
