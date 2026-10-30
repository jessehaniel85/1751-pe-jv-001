package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Um teste por endpoint e por caminho de erro. Cada teste usa uma agência própria, então a ordem não importa.
 */
@QuarkusTest
class AgendamentoResourceTest {

  private static final String CLIENTE = "52998224725";
  private static final String DATA = "2026-12-01";
  private static final AtomicInteger AGENCIAS = new AtomicInteger(7000);

  @Test
  void postAgendaE201ComLocation() {
    String agencia = novaAgencia();
    String location = given().contentType(ContentType.JSON).body(agendamento(agencia, "10:00", CLIENTE))
      .when().post("/agendamentos")
      .then().statusCode(201)
      .header("Location", matchesRegex(".*/agendamentos/\\d+"))
      .body("id", notNullValue())
      .body("agencia", equalTo(agencia))
      .body("horario", equalTo("10:00:00"))
      .extract().header("Location");
    given().when().get(location).then().statusCode(200).body("agencia", equalTo(agencia));
  }

  @Test
  void postNoHorarioOcupadoDevolve409() {
    String agencia = novaAgencia();
    given().contentType(ContentType.JSON).body(agendamento(agencia, "10:30", CLIENTE))
      .when().post("/agendamentos").then().statusCode(201);
    given().contentType(ContentType.JSON).body(agendamento(agencia, "10:30", "11144477735"))
      .when().post("/agendamentos").then().statusCode(409);
  }

  @Test
  void getListaComFiltroPorCpf() {
    String outroCliente = "11144477735";
    String agencia = novaAgencia();
    given().contentType(ContentType.JSON).body(agendamento(agencia, "11:00", outroCliente))
      .when().post("/agendamentos").then().statusCode(201);

    given().when().get("/agendamentos").then().statusCode(200).body("agencia", hasItem(agencia));
    given().queryParam("cpf", outroCliente)
      .when().get("/agendamentos")
      .then().statusCode(200)
      .body("clienteCpf", everyItem(equalTo(outroCliente)))
      .body("agencia", hasItem(agencia));
  }

  @Test
  void getInexistenteDevolve404() {
    given().when().get("/agendamentos/999999").then().statusCode(404);
  }

  @Test
  void putRemarcaELiberaOHorarioAntigo() {
    String agencia = novaAgencia();
    String location = agendar(agencia, "13:00");
    given().contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "14:00"))
      .when().put(location)
      .then().statusCode(200)
      .body("horario", equalTo("14:00:00"));
    given().contentType(ContentType.JSON).body(agendamento(agencia, "13:00", "11144477735"))
      .when().post("/agendamentos").then().statusCode(201);
  }

  @Test
  void putParaHorarioOcupadoDevolve409() {
    String agencia = novaAgencia();
    agendar(agencia, "14:30");
    String location = agendar(agencia, "15:00");
    given().contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "14:30"))
      .when().put(location).then().statusCode(409);
  }

  @Test
  void putParaOProprioHorarioDevolve200() {
    String location = agendar(novaAgencia(), "15:30");
    given().contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "15:30"))
      .when().put(location).then().statusCode(200);
  }

  @Test
  void putInexistenteDevolve404() {
    given().contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "10:00"))
      .when().put("/agendamentos/999999").then().statusCode(404);
  }

  @Test
  void deleteCancelaCom204EDepoisDevolve404() {
    String location = agendar(novaAgencia(), "12:00");
    given().when().delete(location).then().statusCode(204);
    given().when().get(location).then().statusCode(404);
  }

  @Test
  void deleteInexistenteDevolve404() {
    given().when().delete("/agendamentos/999999").then().statusCode(404);
  }

  @Test
  void openApiDocumentaOsEndpoints() {
    given().accept(ContentType.JSON)
      .when().get("/q/openapi")
      .then().statusCode(200)
      .body("info.title", equalTo("API de Agenda de Atendimento"))
      .body("paths", hasKey("/agendamentos"))
      .body("paths", hasKey("/agendamentos/{id}"));
  }

  private static String agendar(String agencia, String horario) {
    return given().contentType(ContentType.JSON).body(agendamento(agencia, horario, CLIENTE))
      .when().post("/agendamentos")
      .then().statusCode(201)
      .extract().header("Location");
  }

  private static Map<String, Object> agendamento(String agencia, String horario, String clienteCpf) {
    return Map.of("agencia", agencia, "data", DATA, "horario", horario, "clienteCpf", clienteCpf);
  }

  private static String novaAgencia() {
    return String.valueOf(AGENCIAS.incrementAndGet());
  }
}
