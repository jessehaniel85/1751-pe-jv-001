package tech.ada.pix.chaves;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.startsWith;

/**
 * Aula 7: autenticação com credenciais reais (HTTP Basic contra a tabela Usuario).
 * Sem @TestSecurity aqui de propósito: o login passa pelo caminho de verdade.
 */
@QuarkusTest
class AutenticacaoTest {

    @Test
    void semCredencialDevolve401ComDesafioBasic() {
        given().when().get("/chaves")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsStringIgnoringCase("basic"));
    }

    @Test
    void senhaErradaDevolve401() {
        given().auth().preemptive().basic("52998224725", "errada")
                .when().get("/chaves")
                .then().statusCode(401);
    }

    @Test
    void usuarioInexistenteDevolve401() {
        given().auth().preemptive().basic("ninguem", "titular123")
                .when().get("/chaves")
                .then().statusCode(401);
    }

    @Test
    void credencialCorretaDevolve200() {
        given().auth().preemptive().basic("52998224725", "titular123")
                .when().get("/chaves")
                .then().statusCode(200);
    }

    @Test
    void healthContinuaAbertoParaOOrquestrador() {
        given().when().get("/q/health/live").then().statusCode(200);
    }

    @Test
    @Transactional
    void senhaFicaGuardadaComoHashBcrypt() {
        Usuario usuario = Usuario.find("username", "52998224725").firstResult();
        assertThat(usuario.senha, startsWith("$2a$"));
    }
}
