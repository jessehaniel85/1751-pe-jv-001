package tech.ada.pix.chaves;

import io.quarkus.test.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Um teste por regra. Cada teste usa uma conta e um e-mail próprios, então a ordem não importa.
 */
@QuarkusTest
class ChavePixResourceTest {

    @InjectMock
    @RestClient
    ContasClient contas;

    @BeforeEach
    void contaExiste() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), "52998224725"));
    }

    private static final String TITULAR = "52998224725";
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final AtomicInteger CONTAS = new AtomicInteger(100000);

    @Inject
    PixConfig config;

    @Test
    void postCriaChaveE201ComLocation() {
        String conta = novaConta();
        String location = given().contentType(ContentType.JSON).body(email(conta, TITULAR))
                .when().post("/chaves")
                .then().statusCode(201)
                .header("Location", matchesRegex(".*/chaves/\\d+"))
                .body("id", notNullValue())
                .body("numeroConta", equalTo(conta))
                .body("criadaEm", notNullValue())
                .extract().header("Location");
        given().when().get(location).then().statusCode(200).body("numeroConta", equalTo(conta));
    }

    @Test
    void postAleatoriaGeraUuidNoServidor() {
        Map<String, Object> corpo = Map.of("tipo", "ALEATORIA",
                "agencia", "0001", "numeroConta", novaConta(), "titularCpf", TITULAR);
        given().contentType(ContentType.JSON).body(corpo)
                .when().post("/chaves")
                .then().statusCode(201)
                .body("valor", matchesRegex(UUID_REGEX));
    }

    @Test
    void postChaveDuplicadaDevolve409() {
        Map<String, Object> corpo = email(novaConta(), TITULAR);
        given().contentType(ContentType.JSON).body(corpo).when().post("/chaves").then().statusCode(201);
        given().contentType(ContentType.JSON).body(corpo).when().post("/chaves").then().statusCode(409);
    }

    @Test
    void postAlemDoLimiteConfiguradoDevolve422() {
        String conta = novaConta();
        for (int i = 0; i < config.chaves().limitePorConta(); i++) {
            given().contentType(ContentType.JSON).body(email(conta, TITULAR)).when().post("/chaves").then().statusCode(201);
        }
        given().contentType(ContentType.JSON).body(email(conta, TITULAR))
                .when().post("/chaves")
                .then().statusCode(422);
    }

    @Test
    void getListaComFiltroPorTitular() {
        String outroTitular = "11144477735";
        Map<String, Object> corpo = email(novaConta(), outroTitular);
        given().contentType(ContentType.JSON).body(corpo).when().post("/chaves").then().statusCode(201);

        given().when().get("/chaves").then().statusCode(200).body("valor", hasItem(corpo.get("valor")));
        given().queryParam("titular", outroTitular)
                .when().get("/chaves")
                .then().statusCode(200)
                .body("titularCpf", everyItem(equalTo(outroTitular)))
                .body("valor", hasItem(corpo.get("valor")));
        given().queryParam("titular", TITULAR)
                .when().get("/chaves")
                .then().statusCode(200)
                .body("valor", not(hasItem(corpo.get("valor"))));
    }

    @Test
    void getPorIdInexistenteDevolve404() {
        given().when().get("/chaves/999999").then().statusCode(404);
    }

    @Test
    void putTrocaAContaVinculada() {
        int id = criar(email(novaConta(), TITULAR));
        String novaConta = novaConta();
        given().contentType(ContentType.JSON).body(Map.of("agencia", "0002", "numeroConta", novaConta))
                .when().put("/chaves/" + id)
                .then().statusCode(200)
                .body("agencia", equalTo("0002"))
                .body("numeroConta", equalTo(novaConta));
    }

    @Test
    void putParaContaCheiaDevolve422() {
        String contaCheia = novaConta();
        for (int i = 0; i < config.chaves().limitePorConta(); i++) {
            criar(email(contaCheia, TITULAR));
        }
        int id = criar(email(novaConta(), TITULAR));
        given().contentType(ContentType.JSON).body(Map.of("agencia", "0001", "numeroConta", contaCheia))
                .when().put("/chaves/" + id)
                .then().statusCode(422);
    }

    @Test
    void putParaAMesmaContaCheiaDevolve200() {
        // A própria chave não conta no limite: a conta cheia já inclui esta chave.
        String contaCheia = novaConta();
        int id = criar(email(contaCheia, TITULAR));
        for (int i = 1; i < config.chaves().limitePorConta(); i++) {
            criar(email(contaCheia, TITULAR));
        }
        given().contentType(ContentType.JSON).body(Map.of("agencia", "0001", "numeroConta", contaCheia))
                .when().put("/chaves/" + id)
                .then().statusCode(200)
                .body("numeroConta", equalTo(contaCheia));
    }

    @Test
    void putEmChaveInexistenteDevolve404() {
        given().contentType(ContentType.JSON).body(Map.of("agencia", "0001", "numeroConta", novaConta()))
                .when().put("/chaves/999999")
                .then().statusCode(404);
    }

    @Test
    void deleteRemoveE204() {
        int id = criar(email(novaConta(), TITULAR));
        given().when().delete("/chaves/" + id).then().statusCode(204);
        given().when().get("/chaves/" + id).then().statusCode(404);
    }

    @Test
    void deleteEmChaveInexistenteDevolve404() {
        given().when().delete("/chaves/999999").then().statusCode(404);
    }

    @Test
    void openApiDocumentaOsEndpoints() {
        given().queryParam("format", "json")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body("paths", hasKey("/chaves/{id}"));
    }

    private static int criar(Map<String, Object> corpo) {
        return given().contentType(ContentType.JSON).body(corpo)
                .when().post("/chaves")
                .then().statusCode(201)
                .extract().path("id");
    }

    private static Map<String, Object> email(String conta, String titular) {
        return Map.of("tipo", "EMAIL", "valor", UUID.randomUUID() + "@exemplo.com.br",
                "agencia", "0001", "numeroConta", conta, "titularCpf", titular);
    }

    private static String novaConta() {
        return String.valueOf(CONTAS.incrementAndGet());
    }
}
