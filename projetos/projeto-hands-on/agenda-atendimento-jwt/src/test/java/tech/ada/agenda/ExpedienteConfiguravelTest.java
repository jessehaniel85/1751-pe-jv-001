package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Aula 4: prova que o expediente vem da configuração. O @TestProfile sobe o Quarkus de novo com outros valores
 * (meio expediente, atendimento de 1 hora), só para esta classe.
 */
@QuarkusTest
@TestProfile(ExpedienteConfiguravelTest.MeioExpediente.class)
class ExpedienteConfiguravelTest {

  public static class MeioExpediente implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
        "agenda.expediente.abertura", "12:00",
        "agenda.expediente.encerramento", "15:00",
        "agenda.expediente.duracao-minutos", "60");
    }
  }

  @Test
  void horariosSeguemOExpedienteConfigurado() {
    given()
      .queryParam("data", "2026-12-07")
    .when()
      .get("/agencias/0001/horarios")
    .then()
      .statusCode(200)
      .body("horarios", contains("12:00:00", "13:00:00", "14:00:00"));
  }
}
