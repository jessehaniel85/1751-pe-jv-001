package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgendaDaAgenciaResourceTest {

  @Test
  void ocupacaoDoDiaListaEmOrdemDeHorario() {
    agendar("0003", "2026-12-02", "14:00");
    agendar("0003", "2026-12-02", "10:00");
    agendar("0003", "2026-12-03", "11:00");

    given()
      .queryParam("data", "2026-12-02")
    .when()
      .get("/agencias/0003/agendamentos")
    .then()
      .statusCode(200)
      .body("horario", contains("10:00:00", "14:00:00"));
  }

  @Test
  void ocupacaoDeDiaSemAgendamentoEVazia() {
    given()
      .queryParam("data", "2026-12-02")
    .when()
      .get("/agencias/0004/agendamentos")
    .then()
      .statusCode(200)
      .body("$", empty());
  }

  private static void agendar(String agencia, String data, String horario) {
    given().contentType(ContentType.JSON)
      .body(Map.of("agencia", agencia, "data", data, "horario", horario, "clienteCpf", "52998224725"))
      .when().post("/agendamentos")
      .then().statusCode(201);
  }

}
