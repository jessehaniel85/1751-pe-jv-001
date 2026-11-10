package tech.ada.calendario;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CalendarioResourceTest {

  @Test
  void diaDeSemanaSemFeriadoEUtil() {
    given().when().get("/agencias/0001/dias/2026-12-01")
      .then().statusCode(200)
      .body("data", equalTo("2026-12-01"))
      .body("util", equalTo(true));
  }

  @Test
  void feriadoNacionalNaoEUtil() {
    given().when().get("/agencias/0002/dias/2026-12-25")
      .then().statusCode(200)
      .body("util", equalTo(false))
      .body("motivo", equalTo("Natal"));
  }

  @Test
  void fimDeSemanaNaoEUtil() {
    given().when().get("/agencias/0002/dias/2026-12-05")
      .then().statusCode(200)
      .body("util", equalTo(false))
      .body("motivo", equalTo("Fim de semana"));
  }

  @Test
  void feriadoMunicipalValeSoParaAAgenciaDaCidade() {
    given().when().get("/agencias/0001/dias/2026-12-08")
      .then().statusCode(200).body("util", equalTo(false)).body("motivo", equalTo("Feriado municipal"));
    given().when().get("/agencias/0002/dias/2026-12-08")
      .then().statusCode(200).body("util", equalTo(true));
  }
}
