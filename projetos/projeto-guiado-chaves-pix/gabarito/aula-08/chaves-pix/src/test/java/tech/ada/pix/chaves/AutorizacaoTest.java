package tech.ada.pix.chaves;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Aula 8: matriz de permissões com credenciais reais (HTTP Basic contra a tabela Usuario).
 *
 * <pre>
 * Operação           titular (só as próprias)   atendente   auditor
 * POST /chaves       sim                        não         não
 * GET  /chaves[/id]  sim                        sim         sim
 * PUT  /chaves/id    sim                        não         não
 * DELETE /chaves/id  sim                        sim         não
 * </pre>
 */
@QuarkusTest
class AutorizacaoTest {

    private static final String MARIA = "52998224725";
    private static final String JOAO = "11144477735";
    private static final AtomicInteger CONTAS = new AtomicInteger(110000);

    @InjectMock
    @RestClient
    ContasClient contas;

    @BeforeEach
    void contaExiste() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), MARIA));
    }

    @Test
    void titularCriaChavePropria() {
        comoTitular(MARIA).contentType(ContentType.JSON).body(email(MARIA))
                .when().post("/chaves")
                .then().statusCode(201);
    }

    @Test
    void titularNaoCriaChaveEmNomeDeOutro() {
        comoTitular(MARIA).contentType(ContentType.JSON).body(email(JOAO))
                .when().post("/chaves")
                .then().statusCode(403)
                .body("erro", equalTo("Acesso negado"));
    }

    @Test
    void atendenteEAuditorNaoCriamChave() {
        como("atendente", "atendente123").contentType(ContentType.JSON).body(email(MARIA))
                .when().post("/chaves").then().statusCode(403).body("status", equalTo(403));
        como("auditor", "auditor123").contentType(ContentType.JSON).body(email(MARIA))
                .when().post("/chaves").then().statusCode(403);
    }

    @Test
    void titularListaSoAsProprias() {
        comoTitular(MARIA).when().get("/chaves")
                .then().statusCode(200)
                .body("titularCpf", everyItem(equalTo(MARIA)));
        comoTitular(MARIA).queryParam("titular", MARIA)
                .when().get("/chaves")
                .then().statusCode(200)
                .body("titularCpf", everyItem(equalTo(MARIA)));
    }

    @Test
    void titularQueFiltraPorOutroTitularRecebe403() {
        comoTitular(MARIA).queryParam("titular", JOAO)
                .when().get("/chaves")
                .then().statusCode(403)
                .body("erro", equalTo("Acesso negado"))
                .body("mensagens", hasItem("Titular só lista as próprias chaves"));
    }

    @Test
    void atendenteListaChavesDeQualquerTitular() {
        como("atendente", "atendente123").queryParam("titular", JOAO)
                .when().get("/chaves")
                .then().statusCode(200)
                .body("valor", hasItem("+5561988887777"));
    }

    @Test
    void titularNaoLeChaveDeOutro() {
        int idDoJoao = criar(JOAO);
        comoTitular(MARIA).when().get("/chaves/" + idDoJoao).then().statusCode(403);
        comoTitular(JOAO).when().get("/chaves/" + idDoJoao).then().statusCode(200);
    }

    @Test
    void auditorLeMasNaoAlteraNemRemove() {
        int id = criar(MARIA);
        como("auditor", "auditor123").when().get("/chaves/" + id).then().statusCode(200);
        como("auditor", "auditor123").contentType(ContentType.JSON).body(novaConta())
                .when().put("/chaves/" + id).then().statusCode(403);
        como("auditor", "auditor123").when().delete("/chaves/" + id).then().statusCode(403);
    }

    @Test
    void atendenteRemoveAPedidoMasNaoTrocaConta() {
        int id = criar(MARIA);
        como("atendente", "atendente123").contentType(ContentType.JSON).body(novaConta())
                .when().put("/chaves/" + id).then().statusCode(403);
        como("atendente", "atendente123").when().delete("/chaves/" + id).then().statusCode(204);
    }

    @Test
    void titularTrocaContaERemoveSoAsProprias() {
        int idDoJoao = criar(JOAO);
        int idDaMaria = criar(MARIA);
        comoTitular(MARIA).contentType(ContentType.JSON).body(novaConta())
                .when().put("/chaves/" + idDoJoao).then().statusCode(403);
        comoTitular(MARIA).when().delete("/chaves/" + idDoJoao).then().statusCode(403);
        comoTitular(MARIA).contentType(ContentType.JSON).body(novaConta())
                .when().put("/chaves/" + idDaMaria).then().statusCode(200);
        comoTitular(MARIA).when().delete("/chaves/" + idDaMaria).then().statusCode(204);
    }

    @Test
    void semCredencialDevolve401() {
        given().when().get("/chaves").then().statusCode(401);
    }

    @Test
    void tiposDeChaveSaoPublicos() {
        given().when().get("/chaves/tipos").then().statusCode(200).body("$", hasItem("ALEATORIA"));
    }

    @Test
    void metricasSoParaAuditor() {
        given().when().get("/q/metrics").then().statusCode(401);
        comoTitular(MARIA).when().get("/q/metrics").then().statusCode(403);
        como("auditor", "auditor123").when().get("/q/metrics").then().statusCode(200);
    }

    @Test
    void healthAbertoSemCredencial() {
        given().when().get("/q/health/ready").then().statusCode(200);
    }

    @Test
    void documentacaoExigeLogin() {
        given().when().get("/q/openapi").then().statusCode(401);
        comoTitular(MARIA).when().get("/q/openapi").then().statusCode(200);
    }

    @Test
    void corsLiberaSoAOrigemConfigurada() {
        given().header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/chaves")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:3000"));
        given().header("Origin", "http://site-desconhecido.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/chaves")
                .then().header("Access-Control-Allow-Origin", nullValue());
    }

    private int criar(String titular) {
        return comoTitular(titular).contentType(ContentType.JSON).body(email(titular))
                .when().post("/chaves")
                .then().statusCode(201)
                .extract().path("id");
    }

    private static RequestSpecification comoTitular(String cpf) {
        return como(cpf, "titular123");
    }

    private static RequestSpecification como(String usuario, String senha) {
        return given().auth().preemptive().basic(usuario, senha);
    }

    private static Map<String, Object> email(String titular) {
        return Map.of("tipo", "EMAIL", "valor", UUID.randomUUID() + "@exemplo.com.br",
                "agencia", "0010", "numeroConta", String.valueOf(CONTAS.incrementAndGet()), "titularCpf", titular);
    }

    private static Map<String, Object> novaConta() {
        return Map.of("agencia", "0010", "numeroConta", String.valueOf(CONTAS.incrementAndGet()));
    }
}
