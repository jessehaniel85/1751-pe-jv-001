# Material do Aluno — Aula 4: configuração, health e métricas

> **Tempo de leitura:** ~15 min. O limite de 5 chaves por conta está numa constante: mudar a regra exige recompilar e publicar de novo. E a plataforma que roda o serviço ainda não tem como perguntar a ele "você está vivo?" e "você está pronto?". Hoje o `chaves-pix` ganha três coisas que todo serviço em produção precisa: **configuração** que muda sem recompilar, **health checks** que respondem às duas perguntas e **métricas** de negócio e de latência. A boa notícia para quem vem do Spring: as métricas usam o **Micrometer**, a mesma biblioteca do Actuator.
>
> As três pausas de sempre: 🎬 **Imagine o cenário**, 💭 **Pare e reflita** e ✍️ **Experimente agora**.

---

## 1. Vivo não é pronto

> 🎬 **Imagine o cenário**
> Um serviço de simulação de empréstimo consignado roda em várias réplicas. O número máximo de parcelas e a taxa de referência mudam por convênio e por ambiente (homologação usa valores de teste). Ontem o banco de dados ficou lento por 2 minutos; a plataforma concluiu que as réplicas estavam "mortas", reiniciou todas ao mesmo tempo, e o serviço ficou fora por muito mais que 2 minutos. A área de negócio também quer saber quantas simulações cada convênio faz por hora, sem abrir chamado. O que estava errado no desenho?

Duas perguntas diferentes, duas reações diferentes da plataforma:

```
 Pergunta            Se a resposta for "não"         Pode consultar o banco?
 ─────────────────   ─────────────────────────────   ───────────────────────
 Está VIVO?          reinicia o processo             NÃO
 (liveness)
 Está PRONTO?        para de mandar tráfego,         SIM
 (readiness)         sem reiniciar
```

Se o *liveness* consulta o banco, um banco lento vira um reinício em massa: exatamente o desastre do cenário.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (é conceito, não versão).

---

## 2. Configuração com MicroProfile Config

O **MicroProfile** é um conjunto de especificações para microsserviços que complementa a Jakarta EE: Config, Health, Fault Tolerance, REST Client, OpenAPI, entre outras. O Quarkus implementa várias delas pelo projeto SmallRye. Duas formas de ler configuração:

**`@ConfigProperty`: uma propriedade, um campo** (o `@Value` do Spring). Exemplo compilado e executado na 3.15.7 (não faz parte do gabarito):

```java
@Path("/limite")
public class LimiteResource {

    @ConfigProperty(name = "pix.chaves.limite-por-conta")
    int limite;

    @ConfigProperty(name = "pix.mensagem-boas-vindas", defaultValue = "Bem-vindo ao diretório de chaves")
    String boasVindas;

    @GET
    public String limite() {
        return boasVindas + " · limite por conta: " + limite;
    }
}
```

```
$ curl localhost:8080/limite
Bem-vindo ao diretório de chaves · limite por conta: 5
```

**`@ConfigMapping`: um grupo de propriedades, tipado** (o `@ConfigurationProperties` do Spring). É o que o gabarito usa (`gabarito/aula-04/.../PixConfig.java`):

```java
@ConfigMapping(prefix = "pix")
public interface PixConfig {

    Chaves chaves();

    interface Chaves {

        /** Regra do DICT para pessoa física: até 5 chaves por conta. */
        int limitePorConta();
    }
}
```

- É uma **interface**, não uma classe nem um `record`.
- O nome do método vira *kebab-case*: `chaves().limitePorConta()` lê `pix.chaves.limite-por-conta`.

No `application.properties`:

```properties
# Aula 4: configuração do domínio, lida por PixConfig (@ConfigMapping).
# Regra do DICT para pessoa física: até 5 chaves por conta.
# Pode ser sobrescrita sem recompilar: variável PIX_CHAVES_LIMITE_POR_CONTA ou -Dpix.chaves.limite-por-conta.
pix.chaves.limite-por-conta=5
```

E no serviço a constante sai (diferença entre `gabarito/aula-03` e `gabarito/aula-04` no `ChavePixService`):

```java
    @Inject
    PixConfig config;

    private void exigirVagaNaConta(String agencia, String numeroConta) {
        int limite = config.chaves().limitePorConta();
        if (repository.countByConta(agencia, numeroConta) >= limite) {
            throw new LimiteDeChavesException(agencia, numeroConta, limite);
        }
    }
```

**Propriedade obrigatória que falta derruba a subida**, não a primeira requisição. Comentamos a linha no `application.properties` e o dev mode recusou subir:

```
java.util.NoSuchElementException: SRCFG00014: The config property pix.chaves.limite-por-conta is required but it could not be found in any config source
```

Se a propriedade for mesmo opcional, diga isso no código: `defaultValue` no `@ConfigProperty`, `@WithDefault("5")` no `@ConfigMapping`, ou `Optional<T>`.

> 💭 **Pare e reflita**
> Você prefere descobrir que falta uma propriedade no momento da implantação ou na primeira requisição de um cliente às 10h da manhã?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no MicroProfile Config, em `@ConfigProperty` e em `@ConfigMapping`.

---

## 3. De onde vem o valor: precedência e sobrescrita

Várias fontes podem definir a mesma propriedade; ganha a de **ordinal maior** (guia `config-reference` da 3.15):

```
 400  propriedade de sistema   -Dpix.chaves.limite-por-conta=2
 300  variável de ambiente     PIX_CHAVES_LIMITE_POR_CONTA=1
 295  arquivo .env             (no diretório de trabalho)
 260  config/application.properties   (ao lado do jar)
 250  application.properties          (dentro do jar)
 100  META-INF/microprofile-config.properties
```

Variável de ambiente: maiúsculas, com `_` no lugar de `.` e de `-`.

Experimento real com o jar da Aula 4 (`mvn package` e `java -jar target/quarkus-app/quarkus-run.jar`), cadastrando três chaves numa conta vazia:

| Como subiu | 1ª chave | 2ª chave | 3ª chave |
|---|---|---|---|
| `PIX_CHAVES_LIMITE_POR_CONTA=1 java -jar ...` | 201 | **422** | 422 |
| `java -Dpix.chaves.limite-por-conta=2 -jar ...` | 201 | 201 | **422** |
| As duas ao mesmo tempo (ambiente = 1, `-D` = 2) | 201 | 201 | **422** (ganhou o `-D`, ordinal 400) |

Nada foi recompilado. Em contêiner, é assim que o ambiente (homologação, produção) entrega valores diferentes para o mesmo artefato.

**Mas nem toda propriedade muda depois do build.** Lembra da Aula 1? O Quarkus faz parte do trabalho no build, e algumas propriedades **ficam fixas** nele, como o tipo de banco (`quarkus.datasource.db-kind`) e o que entra ou não no pacote. No guia de cada extensão, essas propriedades aparecem com um **cadeado**. Exemplo visível: a Swagger UI só entra no pacote em dev e test, e no jar de produção `/q/swagger-ui` respondeu 404. Para incluí-la, existe `quarkus.swagger-ui.always-include=true`, que é propriedade de build: só vale com novo `mvn package`. As propriedades do **nosso** domínio (`pix.*`) são lidas em execução.

> ✍️ **Experimente agora**
> Empacote o seu projeto e rode o jar com `PIX_CHAVES_LIMITE_POR_CONTA=2`. Prove com `curl` que a terceira chave de uma conta nova é recusada. Depois rode com `-Dpix.chaves.limite-por-conta=3` **e** a variável de ambiente ao mesmo tempo. Qual ganhou?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 na ordem de precedência das fontes de configuração.

---

## 4. Perfis e `@TestProfile`

O Quarkus tem três perfis prontos, ativados pelo modo de execução:

| Perfil | Quando está ativo | Prefixo no `application.properties` |
|---|---|---|
| `dev` | `mvn quarkus:dev` | `%dev.` |
| `test` | `@QuarkusTest` | `%test.` |
| `prod` | jar empacotado (o log diz `Profile prod activated.`) | `%prod.` |

Sem prefixo, a propriedade vale para todos. No gabarito:

```properties
# Aula 4: perfis. Sem prefixo = vale em todos; %dev = quarkus:dev; %test = @QuarkusTest; %prod = jar empacotado.
%dev.quarkus.hibernate-orm.log.sql=true
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:chaves-test;DB_CLOSE_DELAY=-1
```

Para testar **outra** configuração, um `QuarkusTestProfile` sobe a aplicação de novo com valores trocados (`gabarito/aula-04/.../LimiteConfiguravelTest.java`):

```java
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
    // email(...) monta o corpo com a conta 0009/900001
}
```

Cada perfil de teste diferente **reinicia** o Quarkus durante a suíte. Poucos perfis, bem agrupados, mantêm os testes rápidos.

> 💭 **Pare e reflita**
> O limite de chaves é regra de negócio. Ele deveria ser configuração ou código? Que argumento você usaria em cada lado? E o que acontece quando um valor de negócio vira configuração sem nenhum teste que prove o seu efeito?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 nos perfis e no `@TestProfile`.

---

## 5. Health checks

Extensão nova: `quarkus-smallrye-health` (sem ela, as anotações de health nem compilam). Ela expõe:

| Endpoint | Pergunta |
|---|---|
| `/q/health/live` | Está vivo? (*liveness*) |
| `/q/health/ready` | Está pronto para receber tráfego? (*readiness*) |
| `/q/health/started` | Terminou de subir? (*startup*) |
| `/q/health` | Todos juntos |

Em dev e test há também a Health UI em `/q/health-ui`.

**O *readiness* do banco vem de graça.** Antes de escrever uma linha de código, `/q/health/ready` já mostra o datasource (saída real):

```json
{
    "status": "UP",
    "checks": [
        {
            "name": "Database connections health check",
            "status": "UP",
            "data": {
                "<default>": "UP"
            }
        }
    ]
}
```

O *pool* de conexões (Agroal) registra esse check sozinho, sem credencial no código. Check manual fica para dependência que o Quarkus não conhece; na Aula 5 será o serviço de contas.

**O *liveness* é nosso** (`gabarito/aula-04/.../ChavesLivenessCheck.java`):

```java
@Liveness
@ApplicationScoped
public class ChavesLivenessCheck implements HealthCheck {

    @Inject
    PixConfig config;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("chaves-pix")
                .withData("limite-por-conta", config.chaves().limitePorConta())
                .up()
                .build();
    }
}
```

Saída real de `/q/health/live` em dev:

```json
{
    "status": "UP",
    "checks": [
        {
            "name": "chaves-pix",
            "status": "UP",
            "data": {
                "limite-por-conta": 5
            }
        }
    ]
}
```

E no jar iniciado com `PIX_CHAVES_LIMITE_POR_CONTA=1`, o mesmo endpoint mostrou o valor que de fato está valendo:

```
{"status":"UP","checks":[{"name":"chaves-pix","status":"UP","data":{"limite-por-conta":1}}]}
```

Ele não consulta banco nem outro serviço: responde só "o processo está aqui e leu a configuração".

**Cuidado com o import do `@Startup`.** O do health é `org.eclipse.microprofile.health.Startup`; existe outro, `io.quarkus.runtime.Startup`, que serve para criar um bean na subida. A IDE costuma sugerir o errado.

> ✍️ **Experimente agora**
> Abra `/q/health/ready` e `/q/health/live` no seu projeto e ache, na saída, o check do datasource e o seu *liveness*. Depois abra a Health UI em `/q/health-ui`.

> **Da 3.15 até a 3.40** — Na **3.17**, datasource sem URL deixa de ter *health check*. Na **3.26**, os sufixos `.enable` passam a `.enabled`; para a Health UI, `quarkus.smallrye-health.ui.enable` vira `quarkus.smallrye-health.ui.enabled`.

---

## 6. Métricas com Micrometer

Extensão nova: `quarkus-micrometer-registry-prometheus`. O endpoint é `/q/metrics`, em texto no formato Prometheus. A API é a mesma do Spring Boot: `MeterRegistry`, `Counter`, `Timer`, `@Timed`, `@Counted`.

**Contador de negócio, com tag**, no serviço (diferença entre `gabarito/aula-03` e `gabarito/aula-04` no `ChavePixService`):

```java
    @Inject
    MeterRegistry registry;

    // em cadastrar(), logo depois do persist:
        registry.counter("pix.chaves.cadastradas", "tipo", chave.tipo.name()).increment();
```

**Latência**, no recurso (`gabarito/aula-04/.../ChavePixResource.java`):

```java
    @POST
    @Timed(value = "pix.chaves.cadastro", description = "Tempo de resposta do cadastro de chave")
    public RestResponse<ChavePixResponse> cadastrar(NovaChaveRequest request, @Context UriInfo uriInfo) {
```

No Quarkus, `@Timed` funciona por interceptor CDI: basta o método estar num bean.

Depois de um `POST` de e-mail, um de telefone e um repetido (409), a saída real de `curl -s localhost:8080/q/metrics | grep -e pix_chaves -e http_server_requests_seconds_count`:

```
# TYPE pix_chaves_cadastro_seconds_max gauge
# HELP pix_chaves_cadastro_seconds_max Tempo de resposta do cadastro de chave
pix_chaves_cadastro_seconds_max{class="tech.ada.pix.chaves.ChavePixResource",exception="ChaveDuplicadaException",method="cadastrar"} 0.00229975
pix_chaves_cadastro_seconds_max{class="tech.ada.pix.chaves.ChavePixResource",exception="none",method="cadastrar"} 0.245345334
# TYPE pix_chaves_cadastro_seconds summary
# HELP pix_chaves_cadastro_seconds Tempo de resposta do cadastro de chave
pix_chaves_cadastro_seconds_count{class="tech.ada.pix.chaves.ChavePixResource",exception="ChaveDuplicadaException",method="cadastrar"} 1.0
pix_chaves_cadastro_seconds_sum{class="tech.ada.pix.chaves.ChavePixResource",exception="ChaveDuplicadaException",method="cadastrar"} 0.00229975
pix_chaves_cadastro_seconds_count{class="tech.ada.pix.chaves.ChavePixResource",exception="none",method="cadastrar"} 2.0
pix_chaves_cadastro_seconds_sum{class="tech.ada.pix.chaves.ChavePixResource",exception="none",method="cadastrar"} 0.247901709
http_server_requests_seconds_count{method="POST",outcome="SUCCESS",status="201",uri="/chaves"} 2.0
http_server_requests_seconds_count{method="POST",outcome="CLIENT_ERROR",status="409",uri="/chaves"} 1.0
# TYPE pix_chaves_cadastradas counter
# HELP pix_chaves_cadastradas  
pix_chaves_cadastradas_total{tipo="TELEFONE"} 1.0
pix_chaves_cadastradas_total{tipo="EMAIL"} 1.0
```

O que ler aí:

- **O nome muda no caminho.** No código é `pix.chaves.cadastradas`; no Prometheus, `.` vira `_` e o contador ganha `_total`. O *timer* vira `_seconds_count`, `_seconds_sum` e `_seconds_max`. Quem procura pelo nome do código não acha.
- **O `@Timed` separa por exceção.** O cadastro recusado como duplicado aparece com `exception="ChaveDuplicadaException"`.
- **As métricas HTTP vêm de graça**, por URI, verbo e status.
- **O contador só aparece depois do primeiro incremento.** `/q/metrics` sem nenhum `POST` não mostra `pix_chaves_cadastradas_total`.

**Cardinalidade.** A tag `tipo` tem 4 valores possíveis. Uma tag com o CPF do titular teria milhões: cada valor vira uma série nova, e o servidor de métricas não aguenta. Tag só com poucos valores, e nunca com dado de pessoa.

> 💭 **Pare e reflita**
> Com `_count` e `_sum` você calcula a média do tempo de cadastro. O que a média **não** conta? Um cadastro de 2 segundos no meio de 1.000 de 10 milissegundos aparece onde?

> **Da 3.15 até a 3.40** — Na **3.31**, `quarkus-smallrye-metrics` (MicroProfile Metrics) é **removida**; nós já usamos Micrometer, que é o caminho recomendado desde a 3.15. Na **3.38**, entra no dev mode um Dev Service de observabilidade (Grafana LGTM) que exige contêiner, e por isso não sobe na rede corporativa.

---

## 7. Testes de observabilidade

Health e métricas também são contrato: o gabarito testa os dois (`gabarito/aula-04/.../ObservabilidadeTest.java`, trecho):

```java
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
```

Saída real do `mvn verify` da Aula 4:

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, ... in tech.ada.pix.chaves.ChavePixRepositoryTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, ... in tech.ada.pix.chaves.ChavePixResourceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, ... in tech.ada.pix.chaves.ObservabilidadeTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, ... in tech.ada.pix.chaves.LimiteConfiguravelTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 nos endpoints `/q/health/*` e `/q/metrics` usados nestes testes.

---

## 8. No Spring era assim

| No Spring Boot | No Quarkus 3.15 |
|---|---|
| `@Value("${pix.limite:5}")` | `@ConfigProperty(name = "pix.limite", defaultValue = "5")` |
| `@ConfigurationProperties(prefix = "pix")` (classe ou `record`) | `@ConfigMapping(prefix = "pix")` (**interface**, *kebab-case* automático) |
| `application-dev.properties` / `application-test.properties` | Prefixos `%dev.` / `%test.` no mesmo arquivo |
| `SPRING_PROFILES_ACTIVE` | `QUARKUS_PROFILE` |
| `@ActiveProfiles` / `@TestPropertySource` | `@TestProfile` (reinicia a aplicação no teste) |
| `/actuator/health/liveness` e `/actuator/health/readiness` | `/q/health/live` e `/q/health/ready` |
| `HealthIndicator` | Classe `@Liveness`/`@Readiness` que implementa `HealthCheck` |
| `DataSourceHealthIndicator` automático | *Readiness* automático do Agroal por datasource |
| `/actuator/prometheus` | `/q/metrics` |
| Micrometer (`MeterRegistry`, `Counter`, `@Timed`) | Micrometer, a mesma API (`@Timed` por interceptor CDI) |

> **Da 3.15 até a 3.40** — as mudanças desta tabela estão nos quadros das seções 5 e 6.

---

**✍️ Desafio da aula**

1. `PixConfig` no seu projeto, com um teste que prova que o limite vem da configuração (`@TestProfile` com outro valor).
2. Rode o jar com `PIX_CHAVES_LIMITE_POR_CONTA=2` e prove com `curl` que a terceira chave de uma conta é recusada.
3. Abra `/q/health/ready` e `/q/metrics` e ache, na saída real, o check do datasource e o contador por tipo.
4. Aprofundamento (opcional): crie um contador de **recusas** (`pix.chaves.recusadas`, com tag `motivo`: `duplicada` ou `limite`) e um teste que o encontre em `/q/metrics`. Ou crie um `@Readiness` que fica `DOWN` quando `pix.manutencao=true`, teste com `@TestProfile` e discuta com o grupo: é uma boa ideia usar *readiness* para "modo manutenção"?

**Para ir além** (guias da versão 3.15)

- Configuração, primeiros passos: https://quarkus.io/version/3.15/guides/config
- Referência de configuração (ordinais, perfis, variáveis de ambiente, `.env`): https://quarkus.io/version/3.15/guides/config-reference
- `@ConfigMapping` (*kebab-case*, `@WithDefault`, grupos): https://quarkus.io/version/3.15/guides/config-mappings
- SmallRye Health (*liveness*, *readiness*, *startup*, Health UI): https://quarkus.io/version/3.15/guides/smallrye-health
- Micrometer (`MeterRegistry`, `@Timed`, cardinalidade, `/q/metrics`): https://quarkus.io/version/3.15/guides/telemetry-micrometer

**Para levar ao projeto final**

- Pelo menos um valor de negócio fora do código, em `@ConfigMapping`, com teste que prove o efeito.
- `%dev` e `%test` separados no `application.properties`.
- Health ligado: *readiness* do banco automático e um *liveness* seu, que não consulta dependências.
- Uma métrica de negócio com tag de baixa cardinalidade, e um teste que a encontre em `/q/metrics`.
- No README do grupo: o que virou configuração e por quê.

> **Na próxima aula:** as chaves vão depender de um serviço de contas. Ele caiu. A chave é cadastrada, recusada ou fica pendente? E quanto tempo você espera por ele antes de desistir?
