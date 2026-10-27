package tech.ada.pix.chaves;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Consultas do repositório contra os dados do import.sql (nenhum outro teste escreve na conta 0001/123456).
 */
@QuarkusTest
class ChavePixRepositoryTest {

    @Inject
    ChavePixRepository repository;

    @Test
    void countByContaContaAsChavesDoImportSql() {
        assertThat(repository.countByConta("0001", "123456"), is(2L));
    }

    @Test
    void findByTitularDevolveAsChavesDoTitular() {
        assertThat(repository.findByTitular("11144477735").stream().map(chave -> chave.valor).toList(),
                hasItem("+5561988887777"));
    }

    @Test
    void getFiltradoPorTitularLeDoBanco() {
        given().queryParam("titular", "52998224725")
                .when().get("/chaves")
                .then().statusCode(200)
                .body("valor", hasItem("maria@exemplo.com.br"));
    }

    @Test
    void chaveDoImportSqlTambemContaComoDuplicada() {
        Map<String, Object> corpo = Map.of("tipo", "CPF", "valor", "52998224725",
                "agencia", "0003", "numeroConta", "999999", "titularCpf", "52998224725");
        given().contentType(ContentType.JSON).body(corpo)
                .when().post("/chaves")
                .then().statusCode(409);
    }

    @Test
    void existeChaveConsultaPorTipoEValor() {
        assertThat(repository.existeChave(TipoChave.EMAIL, "maria@exemplo.com.br"), equalTo(true));
        assertThat(repository.existeChave(TipoChave.CPF, "maria@exemplo.com.br"), equalTo(false));
    }
}
