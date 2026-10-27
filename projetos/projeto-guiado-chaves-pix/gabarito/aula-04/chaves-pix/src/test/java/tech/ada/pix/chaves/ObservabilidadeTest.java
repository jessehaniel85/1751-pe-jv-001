package tech.ada.pix.chaves;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Health (liveness e readiness) e métricas Micrometer no formato Prometheus.
 */
@QuarkusTest
class ObservabilidadeTest {

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
    void cadastroIncrementaContadorPorTipoEMedeOTempo() {
        Map<String, Object> corpo = Map.of("tipo", "EMAIL", "valor", UUID.randomUUID() + "@exemplo.com.br",
                "agencia", "0008", "numeroConta", "800001", "titularCpf", "52998224725");
        given().contentType(ContentType.JSON).body(corpo).when().post("/chaves").then().statusCode(201);

        given().when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("pix_chaves_cadastradas_total{tipo=\"EMAIL\""))
                .body(containsString("pix_chaves_cadastro_seconds_count"));
    }
}
