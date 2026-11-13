package tech.ada.agenda;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Aula 8: matriz de permissões (complemento Keycloak: token Bearer emitido pelo provedor de identidade simulado).
 *
 * <pre>
 * Operação                         cliente (só os próprios)   atendente   gerente
 * POST   /agendamentos             sim                        sim         não
 * GET    /agendamentos[/id]        sim                        sim         sim
 * PUT    /agendamentos/id          sim                        sim         não
 * DELETE /agendamentos/id          sim                        sim         não
 * GET    /agencias/x/agendamentos  não                        sim         sim
 * </pre>
 *
 * Limite de 3 agendamentos por cliente: os clientes de aula são só dois e o banco dos testes é um só. Quem agenda para o
 * cliente de aula cancela no fim do teste; os agendamentos "de outra pessoa" são feitos pelo atendente com um CPF novo.
 */
@QuarkusTest
class AutorizacaoTest {

  private static final String MARIA = "97744180003";
  private static final String JOAO = "43930413051";
  private static final String DATA = "2026-12-10";
  private static final AtomicInteger AGENCIAS = new AtomicInteger(9000);

  @Test
  void clienteAgendaRemarcaECancelaOProprio() {
    String location = comoCliente(MARIA).contentType(ContentType.JSON).body(agendamento(MARIA))
      .when().post("/agendamentos")
      .then().statusCode(201)
      .extract().header("Location");
    comoCliente(MARIA).when().get(location).then().statusCode(200).body("clienteCpf", equalTo(MARIA));
    comoCliente(MARIA).contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "15:30"))
      .when().put(location).then().statusCode(200);
    comoCliente(MARIA).when().delete(location).then().statusCode(204);
  }

  @Test
  void clienteNaoAgendaEmNomeDeOutro() {
    comoCliente(MARIA).contentType(ContentType.JSON).body(agendamento(JOAO))
      .when().post("/agendamentos")
      .then().statusCode(403)
      .body("status", equalTo(403))
      .body("erro", equalTo("Acesso negado"))
      .body("mensagens", hasItem("O agendamento pertence a outro cliente"));
  }

  @Test
  void atendenteAgendaParaQualquerCliente() {
    como("atendente", Papel.ATENDENTE).contentType(ContentType.JSON).body(agendamento(Cpfs.novo()))
      .when().post("/agendamentos")
      .then().statusCode(201);
  }

  @Test
  void gerenteNaoAgendaNemRemarcaNemCancela() {
    String location = agendarParaOutraPessoa();
    como("gerente", Papel.GERENTE).contentType(ContentType.JSON).body(agendamento(Cpfs.novo()))
      .when().post("/agendamentos")
      .then().statusCode(403)
      .body("status", equalTo(403))
      .body("erro", equalTo("Acesso negado"));
    como("gerente", Papel.GERENTE).contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "15:30"))
      .when().put(location).then().statusCode(403);
    como("gerente", Papel.GERENTE).when().delete(location).then().statusCode(403);
    como("gerente", Papel.GERENTE).when().get(location).then().statusCode(200);
  }

  @Test
  void clienteNaoConsultaRemarcaNemCancelaAgendamentoDeOutro() {
    String location = agendarParaOutraPessoa();
    comoCliente(MARIA).when().get(location)
      .then().statusCode(403)
      .body("erro", equalTo("Acesso negado"))
      .body("mensagens", hasItem("O agendamento pertence a outro cliente"));
    comoCliente(MARIA).contentType(ContentType.JSON).body(Map.of("data", DATA, "horario", "15:30"))
      .when().put(location).then().statusCode(403);
    comoCliente(MARIA).when().delete(location).then().statusCode(403);
    // O agendamento continua lá: o 403 não apagou nada.
    como("atendente", Papel.ATENDENTE).when().get(location).then().statusCode(200);
  }

  @Test
  void clienteListaSoOsProprios() {
    agendarParaOutraPessoa();
    // Um agendamento da própria Maria: sem ele, uma lista vazia passaria no everyItem.
    String location = comoCliente(MARIA).contentType(ContentType.JSON).body(agendamento(MARIA))
      .when().post("/agendamentos")
      .then().statusCode(201)
      .extract().header("Location");
    comoCliente(MARIA).when().get("/agendamentos")
      .then().statusCode(200)
      .body("clienteCpf", hasItem(MARIA))
      .body("clienteCpf", everyItem(equalTo(MARIA)));
    comoCliente(MARIA).queryParam("cpf", MARIA)
      .when().get("/agendamentos")
      .then().statusCode(200)
      .body("clienteCpf", hasItem(MARIA))
      .body("clienteCpf", everyItem(equalTo(MARIA)));
    comoCliente(MARIA).when().delete(location).then().statusCode(204);
  }

  @Test
  void clienteQueFiltraPorOutroCpfRecebe403() {
    comoCliente(MARIA).queryParam("cpf", JOAO)
      .when().get("/agendamentos")
      .then().statusCode(403)
      .body("erro", equalTo("Acesso negado"))
      .body("mensagens", hasItem("Cliente só lista os próprios agendamentos"));
  }

  @Test
  void atendenteEGerenteFiltramPorQualquerCpf() {
    String outraPessoa = Cpfs.novo();
    String agencia = novaAgencia();
    como("atendente", Papel.ATENDENTE).contentType(ContentType.JSON).body(agendamento(agencia, outraPessoa))
      .when().post("/agendamentos").then().statusCode(201);
    como("atendente", Papel.ATENDENTE).queryParam("cpf", outraPessoa)
      .when().get("/agendamentos").then().statusCode(200).body("agencia", hasItem(agencia));
    como("gerente", Papel.GERENTE).queryParam("cpf", outraPessoa)
      .when().get("/agendamentos").then().statusCode(200).body("agencia", hasItem(agencia));
  }

  @Test
  void agendaDaAgenciaSoParaAtendenteEGerente() {
    given().when().get("/agencias/0001/agendamentos").then().statusCode(401);
    comoCliente(MARIA).when().get("/agencias/0001/agendamentos").then().statusCode(403).body("erro", equalTo("Acesso negado"));
    como("atendente", Papel.ATENDENTE).when().get("/agencias/0001/agendamentos").then().statusCode(200);
    como("gerente", Papel.GERENTE).when().get("/agencias/0001/agendamentos").then().statusCode(200);
  }

  @Test
  void semCredencialDevolve401() {
    given().when().get("/agendamentos").then().statusCode(401);
    given().contentType(ContentType.JSON).body(agendamento(MARIA))
      .when().post("/agendamentos").then().statusCode(401);
  }

  @Test
  void horariosSaoPublicos() {
    given().when().get("/agencias/0001/horarios").then().statusCode(200);
  }

  @Test
  void metricasSoParaGerente() {
    given().when().get("/q/metrics").then().statusCode(401);
    comoCliente(MARIA).when().get("/q/metrics").then().statusCode(403);
    como("atendente", Papel.ATENDENTE).when().get("/q/metrics").then().statusCode(403);
    como("gerente", Papel.GERENTE).when().get("/q/metrics").then().statusCode(200);
  }

  @Test
  void healthAbertoSemCredencial() {
    given().when().get("/q/health/ready").then().statusCode(200);
  }

  @Test
  void documentacaoExigeLogin() {
    given().when().get("/q/openapi").then().statusCode(401);
    comoCliente(MARIA).when().get("/q/openapi").then().statusCode(200);
  }

  private String agendarParaOutraPessoa() {
    return como("atendente", Papel.ATENDENTE).contentType(ContentType.JSON).body(agendamento(Cpfs.novo()))
      .when().post("/agendamentos")
      .then().statusCode(201)
      .extract().header("Location");
  }

  private static RequestSpecification comoCliente(String cpf) {
    return como(cpf, Papel.USUARIO);
  }

  private static RequestSpecification como(String usuario, String papel) {
    return Tokens.como(usuario, papel);
  }

  private static Map<String, Object> agendamento(String clienteCpf) {
    return agendamento(novaAgencia(), clienteCpf);
  }

  private static Map<String, Object> agendamento(String agencia, String clienteCpf) {
    return Map.of("agencia", agencia, "data", DATA, "horario", "10:00", "clienteCpf", clienteCpf);
  }

  private static String novaAgencia() {
    return String.valueOf(AGENCIAS.incrementAndGet());
  }
}
