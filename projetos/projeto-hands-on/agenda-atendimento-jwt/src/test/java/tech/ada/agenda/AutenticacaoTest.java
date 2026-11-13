package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Complemento JWT: autenticação com token Bearer. O login passa pelo caminho de verdade (POST /login contra a tabela
 * Usuario), e os tokens "ruins" são montados aqui para provar que a API os recusa.
 */
@QuarkusTest
class AutenticacaoTest {

  private static final String CLIENTE = "97744180003";
  private static final ObjectMapper JSON = new ObjectMapper();

  @Inject
  UsuarioRepository usuarios;

  // ---- login ----

  @Test
  void loginDevolveTokenComTresPartesEValidade() {
    String token = given().contentType(ContentType.JSON).body(Map.of("login", CLIENTE, "senha", "senha123"))
      .when().post("/login")
      .then().statusCode(200)
      .body("tipo", equalTo("Bearer"))
      .body("expiraEmSegundos", equalTo(300))
      .extract().path("token");
    assertThat(token.split("\\.").length, equalTo(3));
  }

  @Test
  void tokenCarregaQuemEOQuePode() throws Exception {
    JsonNode cabecalho = parte(Tokens.de(CLIENTE, "senha123"), 0);
    JsonNode claims = parte(Tokens.de(CLIENTE, "senha123"), 1);
    System.out.println("[demo] cabeçalho: " + cabecalho);
    System.out.println("[demo] claims: " + claims);
    assertThat(cabecalho.get("alg").asText(), equalTo("RS256"));
    assertThat(claims.get("upn").asText(), equalTo(CLIENTE));
    assertThat(claims.get("groups").get(0).asText(), equalTo("usuario"));
    assertThat(claims.get("iss").asText(), equalTo("agenda-atendimento"));
    assertThat(claims.get("exp").asLong() - claims.get("iat").asLong(), equalTo(300L));
  }

  @Test
  void senhaErradaEUsuarioInexistenteDevolvemOMesmo401() {
    for (String login : new String[]{CLIENTE, "ninguem"}) {
      given().contentType(ContentType.JSON).body(Map.of("login", login, "senha", "errada"))
        .when().post("/login")
        .then().statusCode(401)
        .body("erro", equalTo("Não autenticado"))
        .body("mensagens", hasItem("Login ou senha inválidos"));
    }
  }

  @Test
  void loginSemSenhaDevolve400() {
    given().contentType(ContentType.JSON).body(Map.of("login", CLIENTE))
      .when().post("/login")
      .then().statusCode(400);
  }

  // ---- token que chega ----

  @Test
  void semTokenDevolve401ComDesafioBearer() {
    given().when().get("/agendamentos")
      .then().statusCode(401)
      .header("WWW-Authenticate", containsStringIgnoringCase("bearer"));
  }

  @Test
  void tokenValidoDevolve200() {
    Tokens.como(CLIENTE, "senha123").when().get("/agendamentos").then().statusCode(200);
  }

  @Test
  void basicNaoEntraMais() {
    given().auth().preemptive().basic(CLIENTE, "senha123")
      .when().get("/agendamentos")
      .then().statusCode(401);
  }

  @Test
  void tokenComPapelTrocadoNaMaoDevolve401() {
    String[] partes = Tokens.de(CLIENTE, "senha123").split("\\.");
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
    String forjado = Jwt.upn("gerente").groups(Set.of("gerente")).sign(chaveDeOutro);
    given().auth().oauth2(forjado).when().get("/q/metrics").then().statusCode(401);
  }

  @Test
  void tokenExpiradoDevolve401() {
    // Dez minutos atrás: bem além da tolerância de relógio de 60 s (mp.jwt.verify.clock.skew).
    String expirado = Jwt.upn(CLIENTE).groups(Set.of("usuario"))
      .issuedAt(Instant.now().minusSeconds(900))
      .expiresAt(Instant.now().minusSeconds(600))
      .sign();
    given().auth().oauth2(expirado).when().get("/agendamentos").then().statusCode(401);
  }

  @Test
  void tokenDeOutroEmissorDevolve401() {
    String deOutroEmissor = Jwt.issuer("outro-sistema").upn(CLIENTE).groups(Set.of("usuario")).sign();
    given().auth().oauth2(deOutroEmissor).when().get("/agendamentos").then().statusCode(401);
  }

  @Test
  void tokenInvalidoBarraAteEndpointPublico() {
    given().when().get("/agencias/0001/horarios").then().statusCode(200);
    given().auth().oauth2("isto.nao.e-um-token").when().get("/agencias/0001/horarios").then().statusCode(401);
  }

  // ---- o que o token muda ----

  @Test
  void quemSouEuLeAsClaimsDoToken() {
    Tokens.como("atendente", "atendente123").when().get("/login/eu")
      .then().statusCode(200)
      .body("login", equalTo("atendente"))
      .body("papeis", contains("atendente"))
      .body("emissor", equalTo("agenda-atendimento"));
    given().when().get("/login/eu").then().statusCode(401);
  }

  @Test
  void tokenContinuaValendoDepoisQueOUsuarioSaiDoBanco() {
    QuarkusTransaction.requiringNew().run(() -> usuarios.persist(Usuario.adicionar("temporario", "temp123", "atendente")));
    String token = Tokens.de("temporario", "temp123");
    QuarkusTransaction.requiringNew().run(() -> usuarios.delete("login", "temporario"));

    // O usuário não existe mais: não consegue um token novo...
    given().contentType(ContentType.JSON).body(Map.of("login", "temporario", "senha", "temp123"))
      .when().post("/login").then().statusCode(401);
    // ...mas o token já emitido segue aceito até expirar: a API confere a assinatura, não o banco.
    given().auth().oauth2(token).when().get("/agencias/0001/agendamentos").then().statusCode(200);
  }

  @Test
  void healthContinuaAbertoParaOOrquestrador() {
    given().when().get("/q/health/live").then().statusCode(200);
  }

  @Test
  @Transactional
  void senhaFicaGuardadaComoHashBcrypt() {
    Usuario usuario = usuarios.findByLogin(CLIENTE).orElseThrow();
    assertThat(usuario.getSenha(), startsWith("$2a$"));
  }

  private static JsonNode parte(String token, int indice) throws Exception {
    return JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[indice]));
  }
}
