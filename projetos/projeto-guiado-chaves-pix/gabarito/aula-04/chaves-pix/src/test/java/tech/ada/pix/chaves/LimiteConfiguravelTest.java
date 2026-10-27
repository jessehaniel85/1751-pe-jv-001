package tech.ada.pix.chaves;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Prova que o limite vem da configuração: com pix.chaves.limite-por-conta=1 (só neste teste),
 * a segunda chave da mesma conta já é recusada. @TestProfile reinicia o Quarkus com a propriedade trocada.
 */
@QuarkusTest
@TestProfile(LimiteConfiguravelTest.LimiteDeUmaChave.class)
class LimiteConfiguravelTest {

    public static class LimiteDeUmaChave implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("pix.chaves.limite-por-conta", "1");
        }
    }

    @Test
    void segundaChaveNaMesmaContaDevolve422() {
        given().contentType(ContentType.JSON).body(email("primeira@exemplo.com.br"))
                .when().post("/chaves").then().statusCode(201);
        given().contentType(ContentType.JSON).body(email("segunda@exemplo.com.br"))
                .when().post("/chaves").then().statusCode(422);
    }

    private static Map<String, Object> email(String valor) {
        return Map.of("tipo", "EMAIL", "valor", valor, "agencia", "0009", "numeroConta", "900001",
                "titularCpf", "52998224725");
    }
}
