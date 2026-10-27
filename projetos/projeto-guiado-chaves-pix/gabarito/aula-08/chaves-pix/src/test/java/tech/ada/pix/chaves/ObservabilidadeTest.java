package tech.ada.pix.chaves;

import io.quarkus.test.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Health (liveness e readiness) e métricas Micrometer no formato Prometheus.
 */
@QuarkusTest
class ObservabilidadeTest {

    @InjectMock
    @RestClient
    ContasClient contas;

    @BeforeEach
    void contaExiste() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), "52998224725"));
    }

    @Test
    void livenessTrazOCheckDaAplicacao() {
        given().when().get("/q/health/live")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("chaves-pix"))
                .body("checks.find { it.name == 'chaves-pix' }.data.limite-por-conta", equalTo(5));
    }

    @Test
    void readinessTrazODatasourceSemCodigo() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("Database connections health check"));
    }

    @Test
    void readinessCaiQuandoOServicoDeContasNaoResponde() {
        when(contas.prontidao()).thenThrow(new ProcessingException("Connection refused"));
        given().when().get("/q/health/ready")
                .then().statusCode(503)
                .body("status", equalTo("DOWN"))
                .body("checks.find { it.name == 'servico-de-contas' }.status", equalTo("DOWN"));
    }

    @Test
    void readinessIncluiOServicoDeContas() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("checks.find { it.name == 'servico-de-contas' }.status", equalTo("UP"));
    }

    @Test
    void cadastroIncrementaContadorPorTipoEMedeOTempo() {
        Map<String, Object> corpo = Map.of("tipo", "EMAIL", "valor", UUID.randomUUID() + "@exemplo.com.br",
                "agencia", "0008", "numeroConta", "800001", "titularCpf", "52998224725");
        given().auth().preemptive().basic("52998224725", "titular123")
                .contentType(ContentType.JSON).body(corpo).when().post("/chaves").then().statusCode(201);

        // Aula 8: /q/metrics só para o papel auditor.
        given().auth().preemptive().basic("auditor", "auditor123")
                .when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("pix_chaves_cadastradas_total{tipo=\"EMAIL\""))
                .body(containsString("pix_chaves_cadastro_seconds_count"));
    }
}
