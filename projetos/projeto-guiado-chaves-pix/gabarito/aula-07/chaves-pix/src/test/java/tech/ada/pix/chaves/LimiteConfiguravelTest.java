package tech.ada.pix.chaves;

import io.quarkus.test.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prova que o limite vem da configuração: com pix.chaves.limite-por-conta=1 (só neste teste),
 * a segunda chave da mesma conta já é recusada. @TestProfile reinicia o Quarkus com a propriedade trocada.
 */
@QuarkusTest
@TestSecurity(user = "52998224725", roles = "titular")
@TestProfile(LimiteConfiguravelTest.LimiteDeUmaChave.class)
class LimiteConfiguravelTest {

    @InjectMock
    @RestClient
    ContasClient contas;

    @BeforeEach
    void contaExiste() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), "52998224725"));
    }

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
