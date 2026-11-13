package tech.ada.agenda;

import static io.restassured.RestAssured.given;

import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.specification.RequestSpecification;
import java.util.Set;

/**
 * Complemento Keycloak: nos testes não há Keycloak. O OidcWiremockTestResource faz o papel do provedor de identidade
 * e assina tokens com o login e o papel pedidos; a Agenda confere esses tokens pelo mesmo caminho de produção.
 */
final class Tokens {

  private Tokens() {
  }

  static String de(String login, String papel) {
    return OidcWiremockTestResource.getAccessToken(login, Set.of(papel));
  }

  static RequestSpecification como(String login, String papel) {
    return given().auth().oauth2(de(login, papel));
  }
}
