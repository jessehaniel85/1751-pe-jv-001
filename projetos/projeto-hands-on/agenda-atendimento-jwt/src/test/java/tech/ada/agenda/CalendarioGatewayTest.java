package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@TestSecurity(user = "atendente", roles = "atendente")
@QuarkusTest
class CalendarioGatewayTest {

  private static final String DATA = "2026-12-01";
  private static final AtomicInteger AGENCIAS = new AtomicInteger(8000);

  @InjectMock
  @RestClient
  CalendarioClient calendario;

  @Inject
  CircuitBreakerMaintenance circuitos;

  @BeforeEach
  @AfterEach
  void fecharCircuitos() {
    circuitos.resetAll();
  }

  @Test
  void feriadoDevolve422SemRetentativa() {
    when(calendario.consultar(anyString(), any())).thenAnswer(
      chamada -> new DiaDoCalendario(chamada.getArgument(1), false, "Feriado municipal"));
    given().contentType(ContentType.JSON).body(agendamento(novaAgencia()))
      .when().post("/agendamentos")
      .then().statusCode(422);
    verify(calendario, times(1)).consultar(anyString(), any());
  }

  @Test
  void calendarioForaTentaTresVezesEDevolve503SemAgendar() {
    when(calendario.consultar(anyString(), any())).thenThrow(new ProcessingException("Connection refused"));
    String agencia = novaAgencia();
    given().contentType(ContentType.JSON).body(agendamento(agencia))
      .when().post("/agendamentos")
      .then().statusCode(503);
    verify(calendario, times(6)).consultar(anyString(), any());
    given().queryParam("data", DATA)
      .when().get("/agencias/" + agencia + "/agendamentos")
      .then().statusCode(200)
      .body("horario", not(hasItem("10:00:00")));
  }

  @Test
  void calendarioLentoViraTimeoutE503() {
    when(calendario.consultar(anyString(), any())).thenAnswer(chamada -> {
      Thread.sleep(800);
      return new DiaDoCalendario(chamada.getArgument(1), true, null);
    });
    given().contentType(ContentType.JSON).body(agendamento(novaAgencia()))
      .when().post("/agendamentos")
      .then().statusCode(503);
  }

  @Test
  void circuitoAbertoNemChamaOCalendario() {
    when(calendario.consultar(anyString(), any())).thenThrow(new ProcessingException("Connection refused"));
    // 1ª requisição: 3 tentativas falhas. 2ª: a 4ª falha completa a janela e o circuito abre.
    given().contentType(ContentType.JSON).body(agendamento(novaAgencia())).when().post("/agendamentos").then().statusCode(503);
    given().contentType(ContentType.JSON).body(agendamento(novaAgencia())).when().post("/agendamentos").then().statusCode(503);
    clearInvocations(calendario);

    given().contentType(ContentType.JSON).body(agendamento(novaAgencia())).when().post("/agendamentos").then().statusCode(503);
    verifyNoInteractions(calendario);
  }

  private static Map<String, Object> agendamento(String agencia) {
    return Map.of("agencia", agencia, "data", DATA, "horario", "10:00", "clienteCpf", Cpfs.novo());
  }

  private static String novaAgencia() {
    return String.valueOf(AGENCIAS.incrementAndGet());
  }

}
