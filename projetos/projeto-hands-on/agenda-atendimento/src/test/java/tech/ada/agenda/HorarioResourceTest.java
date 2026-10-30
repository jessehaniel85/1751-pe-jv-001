package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
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
}
