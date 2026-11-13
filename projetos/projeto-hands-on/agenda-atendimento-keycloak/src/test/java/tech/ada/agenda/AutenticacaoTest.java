package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.smallrye.jwt.build.Jwt;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Complemento Keycloak: autenticação com token Bearer emitido por um provedor de identidade. A Agenda não tem login
 * nem tabela de usuários: só confere o token. O @QuarkusTestResource sobe o provedor simulado para todos os testes.
 */
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class AutenticacaoTest {

  private static final String CLIENTE = "97744180003";

  @Test
  void semTokenDevolve401ComDesafioBearer() {
    given().when().get("/agendamentos")
      .then().statusCode(401)
      .header("WWW-Authenticate", containsStringIgnoringCase("bearer"));
  }

  @Test
  void tokenDoProvedorDevolve200() {
    Tokens.como(CLIENTE, Papel.USUARIO).when().get("/agendamentos").then().statusCode(200);
  }

  @Test
  void basicNaoEntraMais() {
    given().auth().preemptive().basic(CLIENTE, "senha123")
      .when().get("/agendamentos")
      .then().statusCode(401);
  }

  @Test
  void tokenComPapelTrocadoNaMaoDevolve401() {
    String[] partes = Tokens.de(CLIENTE, Papel.USUARIO).split("\\.");
    String claims = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
    String promovido = Base64.getUrlEncoder().withoutPadding()
      .encodeToString(claims.replace("\"usuario\"", "\"gerente\"").getBytes(StandardCharsets.UTF_8));
    given().auth().oauth2(partes[0] + "." + promovido + "." + partes[2])
      .when().get("/q/metrics")
      .then().statusCode(401);
  }

  @Test
  void tokenAssinadoPorOutraChaveDevolve401() throws Exception {
    KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
    gerador.initialize(2048);
    PrivateKey chaveDeOutro = gerador.generateKeyPair().getPrivate();
    String forjado = Jwt.upn("gerente").groups(Set.of(Papel.GERENTE)).sign(chaveDeOutro);
    given().auth().oauth2(forjado).when().get("/q/metrics").then().statusCode(401);
  }

  @Test
  void tokenInvalidoBarraAteEndpointPublico() {
    given().when().get("/agencias/0001/horarios").then().statusCode(200);
    given().auth().oauth2("isto.nao.e-um-token").when().get("/agencias/0001/horarios").then().statusCode(401);
  }

  @Test
  void agendaDaAgenciaTambemExigeToken() {
    given().when().get("/agencias/0001/agendamentos").then().statusCode(401);
    Tokens.como("atendente", Papel.ATENDENTE).when().get("/agencias/0001/agendamentos").then().statusCode(200);
  }

  @Test
  void healthContinuaAbertoParaOOrquestrador() {
    given().when().get("/q/health/live").then().statusCode(200);
  }
}
