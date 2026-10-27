package tech.ada.pix.chaves;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Aula 6: o que entra torto sai como 400, com corpo ErroResposta e mensagem em português.
 */
@QuarkusTest
@TestSecurity(user = "12345678909", roles = "titular")
class ValidacaoTest {

    @InjectMock
    @RestClient
    ContasClient contas;

    @BeforeEach
    void contaExiste() {
        when(contas.buscar(anyString(), anyString()))
                .thenAnswer(chamada -> new ContaResponse(chamada.getArgument(0), chamada.getArgument(1), "12345678909"));
    }

    @Test
    void cpfComDigitoErradoDevolve400() {
        postar(chave("CPF", "12345678900"))
                .statusCode(400)
                .body("status", equalTo(400))
                .body("mensagens", hasItem("valor: CPF inválido (11 dígitos, com dígitos verificadores corretos)"));
    }

    @Test
    void cpfDeOutraPessoaDevolve400() {
        // CPF válido, mas não é o do titular (chave() usa o titular 12345678909): regra do DICT.
        postar(chave("CPF", "52998224725"))
                .statusCode(400)
                .body("status", equalTo(400))
                .body("mensagens", hasItem("valor: a chave CPF precisa ser o CPF do próprio titular (titularCpf)"));
    }

    @Test
    void cpfValidoDevolve201() {
        postar(chave("CPF", "12345678909")).statusCode(201);
    }

    @Test
    void emailSemDominioDevolve400() {
        postar(chave("EMAIL", "maria@exemplo")).statusCode(400).body("mensagens", hasItem("valor: e-mail inválido"));
    }

    @Test
    void telefoneSemMais55Devolve400() {
        postar(chave("TELEFONE", "61988887777")).statusCode(400)
                .body("mensagens", hasItem("valor: telefone inválido (formato +55, DDD e número, ex.: +5561988887777)"));
    }

    @Test
    void telefoneE164Devolve201() {
        String numero = String.valueOf(900000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 99999999L));
        postar(chave("TELEFONE", "+5511" + numero)).statusCode(201);
    }

    @Test
    void aleatoriaComValorDevolve400() {
        postar(chave("ALEATORIA", UUID.randomUUID().toString())).statusCode(400)
                .body("mensagens", hasItem("valor: chave aleatória não recebe valor: o servidor gera o UUID"));
    }

    @Test
    void camposObrigatoriosVoltamTodosDeUmaVez() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("tipo", "EMAIL");
        corpo.put("valor", UUID.randomUUID() + "@exemplo.com.br");
        corpo.put("titularCpf", "123");
        postar(corpo).statusCode(400)
                .body("mensagens", hasItem("agencia: não pode ficar em branco"))
                .body("mensagens", hasItem("numeroConta: não pode ficar em branco"))
                .body("mensagens", hasItem("titularCpf: deve ter 11 dígitos, sem pontos nem traço"));
    }

    @Test
    void tipoAusenteDevolve400() {
        Map<String, Object> corpo = chave("EMAIL", "a@b.com");
        corpo.remove("tipo");
        postar(corpo).statusCode(400).body("mensagens", hasItem("tipo: é obrigatório"));
    }

    @Test
    void putSemContaDevolve400() {
        given().contentType(ContentType.JSON).body(Map.of("agencia", ""))
                .when().put("/chaves/1")
                .then().statusCode(400)
                .body("mensagens", hasItem("numeroConta: não pode ficar em branco"));
    }

    private static ValidatableResponse postar(Map<String, Object> corpo) {
        return given().contentType(ContentType.JSON).body(corpo).when().post("/chaves").then();
    }

    private static Map<String, Object> chave(String tipo, String valor) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("tipo", tipo);
        corpo.put("valor", valor);
        corpo.put("agencia", "0006");
        corpo.put("numeroConta", String.valueOf(600000 + Math.abs(UUID.randomUUID().hashCode() % 99999)));
        corpo.put("titularCpf", "12345678909");
        return corpo;
    }
}
