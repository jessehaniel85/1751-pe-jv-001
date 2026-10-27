package tech.ada.pix.chaves;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ChavePixResourceTest {

    @Test
    void listaAsChavesFixasEmJson() {
        given()
                .when().get("/chaves")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", hasSize(3))
                .body("tipo", hasItems("CPF", "EMAIL", "TELEFONE"));
    }
}
