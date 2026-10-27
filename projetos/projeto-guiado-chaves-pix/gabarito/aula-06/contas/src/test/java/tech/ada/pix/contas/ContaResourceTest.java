package tech.ada.pix.contas;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ContaResourceTest {

    @Test
    void contaExistenteDevolve200() {
        given().when().get("/contas/0001/123456")
                .then().statusCode(200)
                .body("agencia", equalTo("0001"))
                .body("numero", equalTo("123456"))
                .body("titularCpf", equalTo("52998224725"));
    }

    @Test
    void contaInexistenteDevolve404() {
        given().when().get("/contas/9999/000000").then().statusCode(404);
    }

    @Test
    void readinessIncluiOBanco() {
        given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
    }
}
