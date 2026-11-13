package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

/**
 * Aula 7: autenticação com credenciais reais (HTTP Basic contra a tabela Usuario). Sem @TestSecurity aqui, de
 * propósito: o login passa pelo caminho de verdade.
 */
@QuarkusTest
class AutenticacaoTest {

  private static final String CLIENTE = "usuario1";

  @Inject
  UsuarioRepository usuarios;

  @Test
  void semCredencialDevolve401ComDesafioBasic() {
    given().when().get("/agendamentos")
      .then().statusCode(401)
      .header("WWW-Authenticate", containsStringIgnoringCase("basic"));
  }

  @Test
  void senhaErradaDevolve401() {
    given().auth().preemptive().basic(CLIENTE, "errada")
      .when().get("/agendamentos")
      .then().statusCode(401);
  }

  @Test
  void usuarioInexistenteDevolve401() {
    given().auth().preemptive().basic("ninguem", "cliente123")
      .when().get("/agendamentos")
      .then().statusCode(401);
  }

  @Test
  void credencialCorretaDevolve200() {
    given().auth().preemptive().basic(CLIENTE, "senha123")
      .when().get("/agendamentos")
      .then().statusCode(200);
  }

  @Test
  void agendaDaAgenciaTambemExigeLogin() {
    given().when().get("/agencias/0001/agendamentos").then().statusCode(401);
    given().auth().preemptive().basic("atendente", "atendente123")
      .when().get("/agencias/0001/agendamentos")
      .then().statusCode(200);
  }

  @Test
  void horariosSemAnotacaoFicamAbertos() {
    given().when().get("/agencias/0001/horarios").then().statusCode(200);
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
}
