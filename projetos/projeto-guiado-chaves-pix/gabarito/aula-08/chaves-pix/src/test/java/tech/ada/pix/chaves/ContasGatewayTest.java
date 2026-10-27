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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Aula 5: cada política de Fault Tolerance provada por teste, com o serviço de contas simulado (Mockito).
 * O estado do circuito é compartilhado pela aplicação inteira, então cada teste começa com ele fechado.
 */
@QuarkusTest
@TestSecurity(user = "11144477735", roles = "titular")
class ContasGatewayTest {

    private static final String TITULAR = "11144477735";

    @InjectMock
    @RestClient
    ContasClient contas;

    @Inject
    CircuitBreakerMaintenance circuitos;

    @BeforeEach
    @AfterEach
    void fecharCircuitos() {
        circuitos.resetAll();
    }

    @Test
    void contaInexistenteDevolve422SemRetentativa() {
        when(contas.buscar(anyString(), anyString())).thenThrow(new ContaInexistenteException());
        given().contentType(ContentType.JSON).body(novaChave())
                .when().post("/chaves")
                .then().statusCode(422);
        verify(contas, times(1)).buscar(anyString(), anyString());
    }

    @Test
    void putParaContaInexistenteDevolve422() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), TITULAR));
        int id = given().contentType(ContentType.JSON).body(novaChave())
                .when().post("/chaves")
                .then().statusCode(201)
                .extract().path("id");

        when(contas.buscar(anyString(), anyString())).thenThrow(new ContaInexistenteException());
        given().contentType(ContentType.JSON).body(Map.of("agencia", "0005", "numeroConta", "500002"))
                .when().put("/chaves/" + id)
                .then().statusCode(422);
    }

    @Test
    void falhaDoServicoTentaTresVezesEDevolve503SemCadastrar() {
        when(contas.buscar(anyString(), anyString())).thenThrow(new ProcessingException("Connection refused"));
        Map<String, Object> corpo = novaChave();
        given().contentType(ContentType.JSON).body(corpo)
                .when().post("/chaves")
                .then().statusCode(503);
        verify(contas, times(3)).buscar(anyString(), anyString());
        given().queryParam("titular", TITULAR)
                .when().get("/chaves")
                .then().statusCode(200)
                .body("valor", not(hasItem(corpo.get("valor"))));
    }

    @Test
    void respostaLentaViraTimeoutE503() {
        when(contas.buscar(anyString(), anyString())).thenAnswer(chamada -> {
            Thread.sleep(800);
            return new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), TITULAR);
        });
        given().contentType(ContentType.JSON).body(novaChave())
                .when().post("/chaves")
                .then().statusCode(503);
    }

    @Test
    void circuitoAbertoNemChamaOServicoDeContas() {
        when(contas.buscar(anyString(), anyString())).thenThrow(new ProcessingException("Connection refused"));
        // 1ª requisição: 3 tentativas falhas. 2ª: a 4ª falha completa a janela e o circuito abre.
        given().contentType(ContentType.JSON).body(novaChave()).when().post("/chaves").then().statusCode(503);
        given().contentType(ContentType.JSON).body(novaChave()).when().post("/chaves").then().statusCode(503);
        clearInvocations(contas);

        given().contentType(ContentType.JSON).body(novaChave()).when().post("/chaves").then().statusCode(503);
        verifyNoInteractions(contas);
    }

    private static Map<String, Object> novaChave() {
        return Map.of("tipo", "EMAIL", "valor", UUID.randomUUID() + "@exemplo.com.br",
                "agencia", "0005", "numeroConta", "500001", "titularCpf", TITULAR);
    }
}
