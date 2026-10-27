# Material do Aluno — Aula 5: Serviços que conversam (REST Client e Fault Tolerance)

> **Segunda, 09/11/2026 · Tempo de leitura:** ~15 min. Até aqui o `chaves-pix` vivia sozinho. Hoje ele ganha um vizinho, o serviço de `contas`, e passa a depender dele para cadastrar uma chave. Isso traz uma pergunta que nenhuma aula anterior precisou responder: **o que a API faz quando o outro serviço demora ou cai?** No fim da aula, o instrutor também demonstra a imagem nativa do Quarkus, para você entender o que ela é e quando vale a pena.

---

## 1. Quando a chamada pela rede dá errado

🎬 **Imagine o cenário.** Um simulador de crédito consignado precisa consultar o serviço de margem consignável do convênio (quanto do salário do servidor ainda pode ser comprometido). No quinto dia útil, esse serviço demora vários segundos e às vezes cai. As threads do simulador ficam presas esperando, e o simulador para de responder também para quem só queria rever uma simulação antiga. Quando a margem não existe para aquele CPF, o serviço responde `404`.

Nesse cenário há dois tipos de "erro" muito diferentes, e tratá-los igual é o defeito mais comum em integração:

| Tipo | Exemplos | Tentar de novo adianta? | O que a API responde |
|---|---|---|---|
| **Falha técnica** (transitória) | o serviço demorou, recusou a conexão, devolveu 5xx | talvez: pode ser um soluço | se nada funcionar, `503` ("tente mais tarde") |
| **Resposta de negócio** | `404`: a conta (ou a margem) não existe | não: a resposta vai ser a mesma | erro do pedido, por exemplo `422` |

No projeto guiado, a regra de negócio é clara: **chave PIX sem conta confirmada não entra no diretório**. Se o `contas` não responder, o `chaves-pix` devolve `503` e não grava nada. Se responder que a conta não existe, devolve `422`.

💭 **Pare e reflita.** Faz sentido repetir um `POST` de pagamento que deu timeout? E um `GET` de consulta? O que muda entre os dois?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (é conceito, não API).

---

## 2. REST Client: o cliente HTTP é uma interface

No Quarkus, você não escreve o código da chamada HTTP. Você **declara uma interface** com as mesmas anotações de um recurso (`@GET`, `@Path`, `@PathParam`), e o Quarkus gera a implementação no build. A extensão é `quarkus-rest-client-jackson` (cliente do Quarkus REST com JSON).

Do gabarito (`gabarito/aula-05/chaves-pix/src/main/java/tech/ada/pix/chaves/ContasClient.java`):

```java
@Path("/")
@RegisterRestClient(configKey = "contas")
@Produces(MediaType.APPLICATION_JSON)
public interface ContasClient {

    @GET
    @Path("contas/{agencia}/{numero}")
    ContaResponse buscar(@PathParam("agencia") String agencia, @PathParam("numero") String numero);

    @GET
    @Path("q/health/ready")
    String prontidao();

    /**
     * 404 do serviço de contas é resposta de negócio (a conta não existe), não falha técnica:
     * vira ContaInexistenteException, que o Fault Tolerance não repete nem conta no circuito.
     * Para os demais status, null mantém o comportamento padrão (WebApplicationException).
     */
    @ClientExceptionMapper
    static RuntimeException mapear(Response response) {
        return response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()
                ? new ContaInexistenteException()
                : null;
    }
}
```

Quatro coisas para reparar:

1. **A URL não está no código.** O `configKey = "contas"` liga a interface a uma propriedade (`application.properties` do `chaves-pix`):

   ```properties
   quarkus.rest-client.contas.url=http://localhost:8081
   ```

   Em outro ambiente, troque a URL por variável de ambiente, como na Aula 4 (`QUARKUS_REST_CLIENT_CONTAS_URL`), sem recompilar.
2. **Para injetar, o qualificador é obrigatório:** `@Inject @RestClient ContasClient client;`. Sem `@RestClient`, o Quarkus não encontra o bean e o build falha. Saída real, tirando o qualificador do `ContasGateway`:

   ```
   jakarta.enterprise.inject.UnsatisfiedResolutionException: Unsatisfied dependency for type tech.ada.pix.chaves.ContasClient and qualifiers [@Default]
   	- injection target: tech.ada.pix.chaves.ContasGateway#client
   ```
3. **Retorno comum funciona.** O método devolve `ContaResponse`, não `Uni<ContaResponse>`. Como nossos endpoints têm retorno comum, rodam numa *worker thread*, e a chamada bloqueante é permitida ali. `Uni` e `CompletionStage` são opção, não obrigação.
4. **Status ≥ 400 vira exceção.** Por padrão, um `404` do `contas` vira `WebApplicationException`. O `@ClientExceptionMapper` decide o que cada status significa para nós: `404` vira `ContaInexistenteException` (negócio); o resto fica no padrão (falha técnica).

> O pacote do import é `io.quarkus.rest.client.reactive.ClientExceptionMapper`. O "reactive" no nome do pacote é histórico; a extensão é a `quarkus-rest-client-jackson`. Não troque a dependência por causa disso.

O serviço chamado é o módulo novo `contas` (porta 8081), com um único endpoint, `GET /contas/{agencia}/{numero}`. Ele usa Panache no estilo **Active Record** (`Conta.buscar(...)`), de propósito, para você ver o outro estilo da Aula 3.

✍️ **Experimente agora.** Suba o `contas` (`cd contas && mvn quarkus:dev -Ddebug=false`) e, em outro terminal, consulte uma conta que existe e uma que não existe. Saída real:

```
$ curl -i localhost:8081/contas/0001/777777
HTTP/1.1 200 OK
content-length: 63
Content-Type: application/json;charset=UTF-8

{"agencia":"0001","numero":"777777","titularCpf":"12345678909"}

$ curl -i localhost:8081/contas/0001/999999
HTTP/1.1 404 Not Found
content-length: 0
```

> **E o "service discovery"?** Em Kubernetes, `http://contas:8080` resolve pelo DNS do cluster: o nome do *Service* vira endereço. O Quarkus não faz "registro automático" de serviços; quem resolve o nome é a plataforma. Aqui, sem cluster, a URL é fixa por configuração, e o código do cliente é o mesmo nos dois casos.

> **Da 3.15 até a 3.40** — Na **3.16**, a extensão antiga `quarkus-rest-client-reactive*` deixa de resolver (use `quarkus-rest-client*`, como aqui). Na **3.17**, sai a configuração por `[NomeSimples]/mp-rest/url` que aparece em tutoriais antigos; vale `quarkus.rest-client.<configKey>.url`, que já usamos. Na **3.22**, o Stork (descoberta de serviços) deixa de vir junto com o REST Client e precisa ser declarado.

---

## 3. Fault Tolerance: decidir antes o que fazer quando falhar

A extensão `quarkus-smallrye-fault-tolerance` implementa a especificação **MicroProfile Fault Tolerance**. São anotações que você põe no método que faz a chamada remota. No gabarito, elas ficam num bean só para isso, o `ContasGateway` (`gabarito/aula-05/chaves-pix/src/main/java/tech/ada/pix/chaves/ContasGateway.java`):

```java
@ApplicationScoped
public class ContasGateway {

    @Inject
    @RestClient
    ContasClient client;

    @Timeout(500)
    @Retry(maxRetries = 2, abortOn = ContaInexistenteException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, skipOn = ContaInexistenteException.class)
    @Fallback(fallbackMethod = "contasIndisponivel", skipOn = ContaInexistenteException.class)
    public ContaResponse confirmarConta(String agencia, String numeroConta) {
        return client.buscar(agencia, numeroConta);
    }

    ContaResponse contasIndisponivel(String agencia, String numeroConta) {
        throw new ContasIndisponivelException();
    }
}
```

| Anotação | O que faz | No gabarito |
|---|---|---|
| `@Timeout(500)` | desiste da chamada depois de 500 ms | protege as threads do `chaves-pix` |
| `@Retry(maxRetries = 2)` | tenta de novo até 2 vezes (3 chamadas no total) | `abortOn`: conta inexistente não é repetida |
| `@CircuitBreaker(requestVolumeThreshold = 4)` | observa uma janela de 4 chamadas; com falhas suficientes, **abre** e passa a falhar na hora, sem chamar o serviço | `skipOn`: conta inexistente não conta como falha |
| `@Fallback(fallbackMethod = ...)` | plano B quando tudo falhou (ou o circuito está aberto) | lança `ContasIndisponivelException` → `503` |

Por que um bean separado? As anotações funcionam por **interceptador**, como o `@Transactional` da Aula 3. Concentrar a política num `ContasGateway` deixa claro onde ela está e quem a usa. O `ChavePixService` chama `contas.confirmarConta(...)` no `cadastrar` e no `trocarConta`, **depois** de conferir as regras locais (no `cadastrar`, duplicidade e limite; no `trocarConta`, só o limite, e só quando a conta muda): a chamada remota só acontece se valer a pena.

### A ordem em que as anotações se compõem

O comentário do gabarito resume a especificação: **Fallback envolve Retry, que envolve CircuitBreaker, que envolve Timeout.**

```
requisição
   │
   ▼
┌ Fallback ─────────────────────────────┐
│ ┌ Retry (até 3 chamadas) ───────────┐ │
│ │ ┌ CircuitBreaker (janela de 4) ─┐ │ │
│ │ │ ┌ Timeout (500 ms) ─────────┐ │ │ │
│ │ │ │  client.buscar(...)       │ │ │ │
│ │ │ └───────────────────────────┘ │ │ │
│ │ └───────────────────────────────┘ │ │
│ └───────────────────────────────────┘ │
└── se tudo falhar: ContasIndisponivel ─┘
                         → 503
```

Consequência que surpreende: **cada tentativa do retry conta no circuito**. Com o `contas` fora, a 1ª requisição faz 3 chamadas que falham; a 2ª faz a 4ª chamada, que completa a janela, e o circuito abre. A partir da 3ª requisição, o `chaves-pix` responde `503` sem nem tentar chamar o `contas`. Depois de um tempo (o `delay` do `@CircuitBreaker`; o padrão da especificação é 5000 ms), o circuito deixa passar uma chamada de teste e, se ela funcionar, fecha.

💭 **Pare e reflita.** O circuito abrir já na 2ª requisição é bom ou ruim? Pense no serviço de contas voltando aos poucos depois de uma queda, recebendo de uma vez as tentativas de todos os clientes.

### Configuração vence anotação

Os mesmos valores estão no `application.properties` do gabarito, no formato `<classe>/<método>/<Anotação>/<parâmetro>`:

```properties
# Aula 5: Fault Tolerance por configuração (<classe>/<método>/<anotação>/<parâmetro>).
# Valores didáticos, iguais aos das anotações: em produção, saem de medição (latência p99, taxa de erro).
tech.ada.pix.chaves.ContasGateway/confirmarConta/Timeout/value=500
tech.ada.pix.chaves.ContasGateway/confirmarConta/Retry/maxRetries=2
tech.ada.pix.chaves.ContasGateway/confirmarConta/CircuitBreaker/requestVolumeThreshold=4
```

Repare no comentário: **os valores são didáticos**. Em produção, timeout e número de tentativas saem da latência medida do serviço chamado e do tempo que quem chama pode esperar. Ninguém deveria copiar "500 ms" de um material de aula.

✍️ **Experimente agora.** Prove que a propriedade vence a anotação sem mudar uma linha de código. Rode só o teste de Fault Tolerance zerando o retry:

```bash
cd chaves-pix
mvn test -Dtest=ContasGatewayTest '-Dtech.ada.pix.chaves.ContasGateway/confirmarConta/Retry/maxRetries=0'
```

Na nossa execução, 2 dos 5 testes falharam, e a mensagem do Mockito explica por quê:

```
[ERROR] tech.ada.pix.chaves.ContasGatewayTest.falhaDoServicoTentaTresVezesEDevolve503SemCadastrar -- Time elapsed: 0.017 s <<< FAILURE!
Wanted 3 times:
But was 1 time:
[ERROR] Tests run: 5, Failures: 2, Errors: 0, Skipped: 0
```

O outro que falha é `circuitoAbertoNemChamaOServicoDeContas`. Com uma chamada por requisição, duas requisições não completam a janela de 4, e o circuito não abre. É a conta da ordem de composição, agora provada por teste.

✍️ **Experimente agora (com os dois serviços no ar).** `contas` num terminal, `chaves-pix` em outro (`mvn quarkus:dev`). Saídas reais da nossa execução:

```
$ curl -i -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"ALEATORIA","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
HTTP/1.1 201 Created
Location: http://localhost:8080/chaves/151
...

# conta que não existe: 422 na hora, sem retentativa
$ curl -i -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"EMAIL","valor":"ana@exemplo.com.br","agencia":"0001","numeroConta":"999999","titularCpf":"12345678909"}'
HTTP/1.1 422 Unprocessable Entity
content-length: 0
```

Agora derrube o `contas` (`q` no console dele) e repita o `POST` três vezes, medindo o tempo com `-w 'tempo=%{time_total}s\n'`:

```
--- POST 1 com contas fora
HTTP/1.1 503 Service Unavailable
content-length: 0

tempo=0.110463s
--- POST 2 com contas fora
HTTP/1.1 503 Service Unavailable
content-length: 0

tempo=0.091980s
--- POST 3 com contas fora
HTTP/1.1 503 Service Unavailable
content-length: 0

tempo=0.004690s
```

A 1ª requisição fez 3 tentativas; a 2ª fez a 4ª chamada, que abriu o circuito; a 3ª nem chamou o `contas`. Os tempos são da nossa máquina e só servem para comparar entre si: a terceira resposta é instantânea porque o `contas` nem foi chamado. Os erros ainda saem **sem corpo** (`content-length: 0`); na Aula 6 eles ganham um corpo padronizado.

💭 **Pare e reflita.** O `422` depende de o `contas` responder `404` só quando a conta não existe. O que aconteceria se o endereço `quarkus.rest-client.contas.url` apontasse, por engano, para outro serviço que responde `404` a qualquer rota desconhecida?

> **Da 3.15 até a 3.40** — Na **3.18**, a API programática `FaultTolerance.create()` é depreciada em favor de `Guard`/`TypedGuard`. As anotações (`@Timeout`, `@Retry`, `@CircuitBreaker`, `@Fallback`) e a configuração `<classe>/<método>/<Anotação>/<parâmetro>` não mudam.

---

## 4. Testar o caminho de falha sem derrubar nada

Teste de integração que depende do `contas` estar no ar é frágil. O gabarito simula o cliente com Mockito: `@InjectMock` junto de `@RestClient` (dependência de teste `quarkus-junit5-mockito`). Trecho de `gabarito/aula-05/chaves-pix/src/test/java/tech/ada/pix/chaves/ContasGatewayTest.java`:

```java
@QuarkusTest
class ContasGatewayTest {

    @InjectMock
    @RestClient
    ContasClient contas;

    @Inject
    CircuitBreakerMaintenance circuitos;

    @BeforeEach
    @AfterEach
    void fecharCircuitos() {
        circuitos.resetAll();
    }

    @Test
    void falhaDoServicoTentaTresVezesEDevolve503SemCadastrar() {
        when(contas.buscar(anyString(), anyString())).thenThrow(new ProcessingException("Connection refused"));
        Map<String, Object> corpo = novaChave();
        given().contentType(ContentType.JSON).body(corpo)
                .when().post("/chaves")
                .then().statusCode(503);
        verify(contas, times(3)).buscar(anyString(), anyString());
        given().queryParam("titular", TITULAR)
                .when().get("/chaves")
                .then().statusCode(200)
                .body("valor", not(hasItem(corpo.get("valor"))));
    }
```

- O teste prova as **duas metades** da regra: o `503` **e** que a chave não foi gravada (o `GET` não a mostra).
- `verify(contas, times(3))` prova o retry: 1 chamada + 2 retentativas.
- **O circuito é estado da aplicação inteira.** Se um teste abre o circuito, o próximo herda o circuito aberto e falha sem motivo aparente. Por isso `circuitos.resetAll()` antes e depois de cada teste.
- Os outros testes da mesma classe cobrem: conta inexistente → `422` com **uma** chamada só; resposta de 800 ms → timeout e `503`; circuito aberto → nenhuma chamada ao `contas`.

Um detalhe de configuração que evita uma tarde perdida: a porta padrão dos testes do Quarkus é a **8081**, a mesma do `contas` em dev mode. Se o `contas` estiver aberto num terminal, os testes do `chaves-pix` não sobem. O gabarito resolve com `quarkus.http.test-port=0` (porta aleatória) nos dois módulos.

Contagem real do gabarito da Aula 5 (`mvn verify`, 18/10/2026): **34 testes, 0 falhas** (31 no `chaves-pix` + 3 no `contas`).

> **Da 3.15 até a 3.40** — Na **3.31**, a dependência `quarkus-junit5` passa a se chamar `quarkus-junit` (o nome antigo continua resolvendo por redirecionamento). `@InjectMock` e `@RestClient` nos testes: sem mudança relevante até a 3.40.

---

## 5. Prontidão depende do vizinho?

O gabarito acrescenta um `@Readiness` que pergunta ao `contas` se ele está pronto (`gabarito/aula-05/chaves-pix/src/main/java/tech/ada/pix/chaves/ContasReadinessCheck.java`). Com o `contas` fora, o `/q/health/ready` do `chaves-pix` fica `DOWN`. Trecho real da resposta:

```
$ curl -i localhost:8080/q/health/ready
HTTP/1.1 503 Service Unavailable
...
    "checks": [
        {
            "name": "servico-de-contas",
            "status": "DOWN",
```

💭 **Pare e reflita.** O comentário do gabarito deixa a pergunta em aberto, e ela é sua: com o `contas` fora, o `GET /chaves` ainda funcionaria, mas o orquestrador tira a instância inteira do balanceamento. Vale a pena amarrar a prontidão de um serviço à de outro? Escreva a sua resposta em três linhas; ela é boa evidência para a banca.

> **Da 3.15 até a 3.40** — Na **3.26**, os sufixos `.enable` de configuração passam a `.enabled`, inclusive o da Health UI. `@Readiness`, `@Liveness` e `/q/health/*`: sem mudança relevante até a 3.40.

---

## 6. Imagem nativa: o que o instrutor vai demonstrar

Até agora, toda aplicação Quarkus que você rodou foi um `quarkus-run.jar` numa JVM. A **imagem nativa** é outra forma de empacotar a mesma aplicação: a ferramenta `native-image`, da GraalVM, compila o código Java **antes da execução** (compilação AOT, *ahead-of-time*) e gera um executável do sistema operacional, que roda sem JVM.

```
Modo JVM:     código ─► .class ─► quarkus-run.jar ─► JVM carrega, interpreta e otimiza (JIT) em execução
Modo nativo:  código ─► .class ─► native-image analisa tudo no build ─► executável (sem JVM)
```

**Por que importa.** O Quarkus já faz no build boa parte do trabalho que outros frameworks fazem na subida (Aula 1). A imagem nativa leva isso ao extremo: o executável sobe mais rápido e usa menos memória que o modo JVM. Isso pesa quando há muitas réplicas, quando o serviço precisa subir do zero sob demanda (funções, escala a partir de zero) e em ferramentas de linha de comando.

**O preço.**
- **Build bem mais lento e pesado** que o `mvn package`, e esse tempo entra na esteira de entrega.
- **Mundo fechado:** o `native-image` precisa saber no build tudo o que será usado. Reflexão e recursos carregados dinamicamente precisam ser declarados; as extensões do Quarkus fazem isso por você, mas bibliotecas de fora podem exigir configuração.
- Depuração e perfilamento diferentes dos da JVM.

**O que o build exige** (guia `building-native-image` do Quarkus 3.15): uma GraalVM ou Mandrel para JDK 21 com `GRAALVM_HOME` configurado, **e** um compilador C com as bibliotecas do sistema (no Linux, GCC e os headers de glibc e zlib; no macOS, as Xcode Command Line Tools; no Windows, as ferramentas de build do Visual Studio com C++). A alternativa de compilar dentro de um contêiner (`-Dquarkus.native.container-build=true`) exige Docker ou Podman.

Por isso, **na rede corporativa você acompanha a demonstração do instrutor**, feita na máquina dele com o binário compilado antes da aula. O comando que ele usou parte do perfil `native` que o plugin já gerou no seu `pom.xml`:

```bash
mvn install -Dnative -DskipTests
```

Os números da comparação (tempo de build, tamanho, subida, memória) saem da demonstração, medidos ao vivo na mesma máquina. Não use números de blog: dependem do hardware, do projeto e da versão.

✍️ **Experimente agora.** Meça o seu modo JVM para comparar com os números nativos da demo:

```bash
mvn package -DskipTests
java -jar chaves-pix/target/quarkus-app/quarkus-run.jar
# anote o tempo da linha do log:  ... started in X.XXXs. Listening on: http://0.0.0.0:8080
# em outro terminal, a memória residente (KB) depois da 1ª requisição:
curl -s -o /dev/null localhost:8080/chaves
ps -o rss= -p $(pgrep -f quarkus-run.jar)
```

Poste o seu "started in" no chat. Lembre: RSS não é heap, e a comparação só vale na mesma máquina.

💭 **Pare e reflita.** Na esteira da sua equipe, quem decidiria usar imagem nativa: você, o time de plataforma ou os dois? O que você precisaria garantir no código para que essa decisão fosse possível?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 registrada na revisão: o build nativo continua com GraalVM ou Mandrel para JDK 21, e o build em contêiner continua exigindo Docker ou Podman.

---

### No Spring era assim

| Você fazia no Spring Boot | No Quarkus |
|---|---|
| `@FeignClient(name = "contas", url = ...)` ou interface `@HttpExchange` | interface `@RegisterRestClient(configKey = "contas")` |
| `RestTemplate` / `WebClient` / `RestClient` montados à mão | não precisa: a interface é implementada no build |
| `@Autowired ContasClient` | `@Inject @RestClient ContasClient` (qualificador obrigatório) |
| `ErrorDecoder` (Feign) ou `onStatus(...)` (WebClient) | `@ClientExceptionMapper` na interface |
| Resilience4j: `@TimeLimiter`, `@Retry`, `@CircuitBreaker(fallbackMethod = ...)` | MicroProfile: `@Timeout`, `@Retry`, `@CircuitBreaker`, `@Fallback` (anotações separadas) |
| `ignoreExceptions` no Resilience4j | `abortOn` (Retry) e `skipOn` (CircuitBreaker, Fallback) |
| `resilience4j.retry.instances.contas.max-attempts` no yml | `<classe>/<método>/Retry/maxRetries` no `application.properties` |
| `@MockBean ContasClient` | `@InjectMock @RestClient ContasClient` |
| Spring AOT + GraalVM Native Build Tools | perfil `native` já gerado: `mvn install -Dnative` |

Duas diferenças de vocabulário que confundem: `@TimeLimiter` do Resilience4j exige retorno assíncrono, e o `@Timeout` funciona em método comum; e `maxRetries` conta só as **novas** tentativas (com `maxRetries = 2`, são 3 chamadas, como o `times(3)` do teste mostra).

---

**Para ir além** (guias oficiais da 3.15)

- REST Client: https://quarkus.io/version/3.15/guides/rest-client
- SmallRye Fault Tolerance: https://quarkus.io/version/3.15/guides/smallrye-fault-tolerance
- Health (readiness): https://quarkus.io/version/3.15/guides/smallrye-health
- Imagem nativa: https://quarkus.io/version/3.15/guides/building-native-image
- Descoberta de serviços com Stork (leitura opcional): https://quarkus.io/version/3.15/guides/stork

**Para levar ao projeto final** (critério 2 da rubrica, marco desta aula: integração rodando)

- Um serviço chama o outro por REST Client, com a URL em `quarkus.rest-client.<configKey>.url`.
- Uma política de Fault Tolerance **justificada** num parágrafo do README: o que é falha técnica, o que é resposta de negócio, qual é o fallback e por quê.
- Pelo menos um teste do caminho de falha com `@InjectMock @RestClient`: o serviço chamado "fora" e o fallback ou o erro tratado acionado. Feche os circuitos entre os testes.
- `quarkus.http.test-port=0` nos dois módulos.

> **Na próxima aula:** hoje a API aceita CPF com dígito verificador errado, telefone sem DDI e e-mail sem arroba, e os erros saem sem corpo. Quem deveria recusar, e com que mensagem?
