package tech.ada.agenda;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;

/** Complemento JWT: faz o login de verdade (POST /login) e devolve a requisição já com o cabeçalho Bearer. */
final class Tokens {

  private Tokens() {
  }

  static String de(String login, String senha) {
    return given().contentType(ContentType.JSON).body(Map.of("login", login, "senha", senha))
      .when().post("/login")
      .then().statusCode(200)
      .extract().path("token");
  }

  static RequestSpecification como(String login, String senha) {
    return given().auth().oauth2(de(login, senha));
  }
}
