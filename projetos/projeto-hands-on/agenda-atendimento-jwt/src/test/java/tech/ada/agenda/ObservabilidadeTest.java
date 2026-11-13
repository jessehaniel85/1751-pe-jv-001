package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Aula 4: health (readiness do banco, sem código nosso) e métrica de negócio no formato Prometheus.
 */
@QuarkusTest
class ObservabilidadeTest {

  @Test
  void readinessTrazOBancoSemCodigo() {
    given().when().get("/q/health/ready")
      .then().statusCode(200)
      .body("status", equalTo("UP"))
      .body("checks.name", hasItem("Database connections health check"));
  }

  @Test
  void agendarIncrementaOContadorDaAgencia() {
    Tokens.como("atendente", "atendente123")
      .contentType(ContentType.JSON)
      .body(Map.of("agencia", "0042", "data", "2026-12-08", "horario", "10:00", "clienteCpf", Cpfs.novo()))
      .when().post("/agendamentos").then().statusCode(201);

    Tokens.como("gerente", "gerente123")
      .when().get("/q/metrics")
      .then().statusCode(200)
      .body(containsString("agenda_agendamentos_criados_total{agencia=\"0042\"}"));
  }
}
