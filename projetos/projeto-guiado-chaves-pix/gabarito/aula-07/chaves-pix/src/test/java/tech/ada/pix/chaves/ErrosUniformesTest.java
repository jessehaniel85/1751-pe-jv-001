package tech.ada.pix.chaves;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Aula 6: toda resposta de erro tem o mesmo formato (ErroResposta), qualquer que seja o status.
 */
@QuarkusTest
@TestSecurity(user = "52998224725", roles = "titular")
class ErrosUniformesTest {

    @InjectMock
    @RestClient
    ContasClient contas;

    @Inject
    CircuitBreakerMaintenance circuitos;

    @Inject
    PixConfig config;

    @BeforeEach
    @AfterEach
    void fecharCircuitos() {
        circuitos.resetAll();
    }

    @BeforeEach
    void contaExiste() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), "12345678909"));
    }

    @Test
    void naoEncontradaDevolve404ComCorpo() {
        given().when().get("/chaves/999999")
                .then().statusCode(404)
                .contentType(ContentType.JSON)
                .body("status", equalTo(404))
                .body("erro", equalTo("Chave não encontrada"))
                .body("mensagens", hasSize(1));
    }

    @Test
    void duplicadaDevolve409ComCorpo() {
        given().contentType(ContentType.JSON).body(email("0007", "700001", "maria@exemplo.com.br"))
                .when().post("/chaves")
                .then().statusCode(409)
                .body("status", equalTo(409))
                .body("erro", equalTo("Chave duplicada"));
    }

    @Test
    void limiteDevolve422ComCorpo() {
        for (int i = 0; i < config.chaves().limitePorConta(); i++) {
            given().contentType(ContentType.JSON).body(email("0007", "700002", UUID.randomUUID() + "@exemplo.com.br"))
                    .when().post("/chaves").then().statusCode(201);
        }
        given().contentType(ContentType.JSON).body(email("0007", "700002", UUID.randomUUID() + "@exemplo.com.br"))
                .when().post("/chaves")
                .then().statusCode(422)
                .body("status", equalTo(422))
                .body("erro", equalTo("Limite de chaves por conta"));
    }

    @Test
    void contaInexistenteDevolve422ComCorpo() {
        when(contas.buscar(anyString(), anyString())).thenThrow(new ContaInexistenteException());
        given().contentType(ContentType.JSON).body(email("0007", "700003", UUID.randomUUID() + "@exemplo.com.br"))
                .when().post("/chaves")
                .then().statusCode(422)
                .body("erro", equalTo("Conta inexistente"));
    }

    @Test
    void contasForaDevolve503ComCorpo() {
        when(contas.buscar(anyString(), anyString())).thenThrow(new ProcessingException("Connection refused"));
        given().contentType(ContentType.JSON).body(email("0007", "700004", UUID.randomUUID() + "@exemplo.com.br"))
                .when().post("/chaves")
                .then().statusCode(503)
                .body("status", equalTo(503))
                .body("erro", equalTo("Serviço de contas indisponível"));
    }

    private static Map<String, Object> email(String agencia, String conta, String valor) {
        return Map.of("tipo", "EMAIL", "valor", valor, "agencia", agencia, "numeroConta", conta,
                "titularCpf", "12345678909");
    }
}
