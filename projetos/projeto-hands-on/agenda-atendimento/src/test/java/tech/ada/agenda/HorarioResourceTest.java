package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HorarioResourceTest {

  @Test
  void listar() {
    //given-when-then
    given()
      .queryParam("data", "2026-10-30")
    .when()
      .get("/agencias/0001/horarios")
    .then()
      .statusCode(200)
      .contentType("application/json")
      .body("agencia", is("0001"))
      .body("data", is("2026-10-30"))
      .body("horarios", hasSize(12));
  }

  @Test
  void semDataUsaOAmanha() {
    given()
      .when()
      .get("/agencias/0001/horarios")
      .then()
      .statusCode(200)
      .body("data", is(LocalDate.now().plusDays(1).toString()));
  }

  @Test
  void horarioAgendadoSaiDaLista() {
    Map<String, Object> agendamento = Map.of("agencia", "0002", "data", "2026-10-29",
      "horario", "10:30", "clienteCpf", "52998224725");
    given().contentType(ContentType.JSON).body(agendamento)
      .when().post("/agendamentos").then().statusCode(201);

    given()
      .queryParam("data", "2026-10-29")
      .when()
      .get("/agencias/0002/horarios")
      .then()
      .statusCode(200)
      .body("horarios", hasSize(11))
      .body("horarios", not(hasItem("10:30:00")));
  }
}
