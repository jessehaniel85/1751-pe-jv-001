# Material do Aluno — Aula 2: Quarkus REST, CDI e OpenAPI

> **Tempo de leitura:** ~15 min. Na Aula 1 o `ChavePixResource` devolvia uma lista fixa e fazia tudo sozinho. Hoje ele ganha o CRUD completo e para de fazer tudo sozinho: o recurso só traduz HTTP, um **serviço** guarda as regras e um **repositório** guarda os dados (ainda em memória; o banco chega na Aula 3). No caminho você vai encontrar três diferenças grandes em relação ao Spring: não existe `ResponseEntity`, não existe `@Service`, e quem confere as suas injeções de dependência é o **build**.
>
> As três pausas de sempre: 🎬 **Imagine o cenário**, 💭 **Pare e reflita** e ✍️ **Experimente agora**.

---

## 1. Por que separar em camadas

> 🎬 **Imagine o cenário**
> Uma agência quer uma API para o cliente agendar atendimento presencial: escolher um horário, consultar os próprios agendamentos, remarcar e cancelar. Dois clientes tentam o mesmo horário ao mesmo tempo; um tenta remarcar um agendamento que não existe; outro tenta agendar num dia sem expediente. Qual verbo, qual URI e qual código em cada caso? E onde mora a regra "um horário, um cliente": no recurso HTTP, num serviço ou no armazenamento?

Se a regra mora no recurso HTTP, ela só vale para quem chega por HTTP. No dia em que outro canal (uma fila, um processamento em lote) precisar agendar, a regra é copiada ou esquecida. Por isso o projeto guiado fica assim:

```
 HTTP ──► ChavePixResource ──► ChavePixService ──► ChavePixRepository
          (verbos, status,      (regras: duplicada,   (guarda e consulta;
           URI, JSON)            limite por conta)     hoje um Map)
```

- **Recurso:** recebe a requisição, chama o serviço e escolhe a resposta HTTP.
- **Serviço:** conhece as regras de negócio. Não sabe nada de HTTP.
- **Repositório:** guarda e consulta. Não conhece regra nenhuma.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (é desenho, não versão).

---

## 2. Quarkus REST: recursos e respostas

O servidor REST do Quarkus se chama **Quarkus REST** (é o antigo RESTEasy Reactive, renomeado na 3.9). Ele implementa **Jakarta REST** (`jakarta.ws.rs.*`): `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`, `@Produces`, `@Consumes`, `@PathParam`, `@QueryParam`. E acrescenta atalhos próprios no pacote `org.jboss.resteasy.reactive`, como `RestResponse<T>`.

Como devolver a resposta:

| Retorno do método | Status | Quando usar |
|---|---|---|
| Objeto ou `record` | 200, com o corpo em JSON | Consulta e atualização que devolvem o recurso |
| `void` | **204**, sem corpo | `DELETE` |
| `jakarta.ws.rs.core.Response` | o que você montar | Controle total, sem tipo |
| `RestResponse<T>` | o que você montar | Controle total, **com tipo** |

O recurso completo está em `gabarito/aula-02/chaves-pix/src/main/java/tech/ada/pix/chaves/ChavePixResource.java`:

```java
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    @Inject
    ChavePixService service;

    @POST
    public RestResponse<ChavePixResponse> cadastrar(NovaChaveRequest request, @Context UriInfo uriInfo) {
        ChavePix chave = service.cadastrar(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(chave.getId().toString()).build();
        return RestResponse.ResponseBuilder.<ChavePixResponse>created(location).entity(ChavePixResponse.de(chave)).build();
    }

    @GET
    public List<ChavePixResponse> listar(@QueryParam("titular") String titularCpf) {
        return service.listar(titularCpf).stream().map(ChavePixResponse::de).toList();
    }

    @GET
    @Path("/{id}")
    public ChavePixResponse buscar(@PathParam("id") Long id) {
        return ChavePixResponse.de(service.buscar(id));
    }

    @PUT
    @Path("/{id}")
    public ChavePixResponse trocarConta(@PathParam("id") Long id, TrocaContaRequest request) {
        return ChavePixResponse.de(service.trocarConta(id, request));
    }

    @DELETE
    @Path("/{id}")
    public void remover(@PathParam("id") Long id) {
        service.remover(id);
    }
}
```

Dois detalhes:

- No `POST`, `UriInfo` monta a URI do recurso criado, que vai no cabeçalho `Location` (nível 2 de Richardson).
- `RestResponse.ResponseBuilder.<ChavePixResponse>created(...)` precisa do tipo explícito entre `< >`. Sem ele, o compilador infere `Object` e o código não compila.

Os corpos de entrada e saída são `record` (do mesmo gabarito):

```java
public record NovaChaveRequest(
        TipoChave tipo,
        String valor,
        String agencia,
        String numeroConta,
        String titularCpf) {
}

public record TrocaContaRequest(String agencia, String numeroConta) {
}
```

A resposta converte o modelo num método de fábrica do próprio *record* (`ChavePixResponse.java`). A `ChavePix`, que o serviço guarda, é uma classe comum com `id`, `tipo`, `valor`, `agencia`, `numeroConta`, `titularCpf` e `criadaEm`, com getters e setters (veja o gabarito):

```java
public record ChavePixResponse(Long id, TipoChave tipo, String valor, String agencia, String numeroConta,
                               String titularCpf, LocalDateTime criadaEm) {

    public static ChavePixResponse de(ChavePix chave) {
        return new ChavePixResponse(chave.getId(), chave.getTipo(), chave.getValor(), chave.getAgencia(),
                chave.getNumeroConta(), chave.getTitularCpf(), chave.getCriadaEm());
    }
}
```

As respostas reais, numa mesma execução do gabarito, em sequência:

```
$ curl -i -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"EMAIL","valor":"maria@exemplo.com.br","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725"}'
HTTP/1.1 201 Created
content-length: 161
Content-Type: application/json;charset=UTF-8
Location: http://localhost:8080/chaves/1

{"id":1,"tipo":"EMAIL","valor":"maria@exemplo.com.br","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725","criadaEm":"2026-10-17T10:36:01.745627"}
```

Para chave **aleatória**, o cliente não manda valor; o servidor gera um UUID:

```
$ curl -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"ALEATORIA","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725"}'
{"id":2,"tipo":"ALEATORIA","valor":"57a8de2f-c132-4f51-b838-6faf58018d11","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725","criadaEm":"2026-10-17T10:36:01.792605"}
```

Troca de conta e remoção:

```
$ curl -i -X PUT localhost:8080/chaves/1 -H 'Content-Type: application/json' \
  -d '{"agencia":"0002","numeroConta":"654321"}'
HTTP/1.1 200 OK
content-length: 161
Content-Type: application/json;charset=UTF-8

{"id":1,"tipo":"EMAIL","valor":"maria@exemplo.com.br","agencia":"0002","numeroConta":"654321","titularCpf":"52998224725","criadaEm":"2026-10-17T10:36:01.745627"}

$ curl -i -X DELETE localhost:8080/chaves/2
HTTP/1.1 204 No Content
```

> 💭 **Pare e reflita**
> `NovaChaveRequest` e `ChavePixResponse` são `record`. A `ChavePix` (o modelo que o serviço guarda) é uma classe comum, com getters e setters. Por que a diferença? Pista: o que o `PUT` faz com ela, e o que acontece com ela na Aula 3?

> **Da 3.15 até a 3.40** — Na **3.16** deixam de resolver `quarkus-resteasy-reactive` e `quarkus-resteasy-reactive-jackson`, os nomes anteriores à renomeação da 3.9: quem copiar um `pom.xml` antigo quebra o build. As anotações de Jakarta REST e o `RestResponse<T>` não mudam.

---

## 3. Erros com status, por enquanto

Até a Aula 6, quando entra o tratamento de erros com corpo padronizado, cada exceção do projeto **já carrega o status HTTP**, porque estende uma exceção do próprio Jakarta REST (`gabarito/aula-02/.../ChaveDuplicadaException.java` e vizinhas):

```java
public class ChaveDuplicadaException extends ClientErrorException {

    public ChaveDuplicadaException(TipoChave tipo, String valor) {
        super("Chave " + tipo + " " + valor + " já está cadastrada", Response.Status.CONFLICT);
    }
}

public class LimiteDeChavesException extends ClientErrorException {

    private static final int UNPROCESSABLE_ENTITY = 422;

    public LimiteDeChavesException(String agencia, String numeroConta, int limite) {
        super("A conta " + agencia + "/" + numeroConta + " já tem " + limite + " chaves", UNPROCESSABLE_ENTITY);
    }
}

public class ChaveNaoEncontradaException extends NotFoundException {

    public ChaveNaoEncontradaException(Long id) {
        super("Chave " + id + " não encontrada");
    }
}
```

O serviço só lança; o Quarkus REST transforma no status. Respostas reais:

```
POST da mesma chave pela 2ª vez       → HTTP/1.1 409 Conflict
6º POST na mesma conta                 → HTTP/1.1 422 Unprocessable Entity
GET /chaves/999                        → HTTP/1.1 404 Not Found
DELETE /chaves/2 pela 2ª vez           → HTTP/1.1 404 Not Found
```

Repare que, por enquanto, o corpo do erro sai **vazio** (`content-length: 0`). Quem chama sabe *que* falhou, mas não *por quê*. Isso se resolve na Aula 6.

O limite de 5 chaves por conta (regra do DICT para pessoa física) está numa constante no serviço, com um aviso: "vira configuração na Aula 4".

> 💭 **Pare e reflita**
> O `PUT /chaves/{id}` troca a conta da chave. Se a conta de destino já tem 5 chaves, o gabarito responde 422. E se a conta de destino for **a mesma** em que a chave já está, e ela estiver cheia? Antes de rodar, decida o que deveria acontecer. No gabarito, a própria chave **não** conta no limite: o `PUT` responde 200 (teste `putParaAMesmaContaCheiaDevolve200`). Uma primeira versão contava a chave duas vezes e respondia 422. Que teste teria pegado isso antes?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 em `ClientErrorException`/`NotFoundException`.

---

## 4. Ligação de parâmetros: a regra completa

Parâmetro anotado (`@PathParam`, `@QueryParam`, `@HeaderParam`) é ligado à parte da requisição que a anotação indica. E parâmetro **sem** anotação? A regra do Quarkus REST tem duas partes:

1. Se o nome do parâmetro **coincide** com uma variável do `@Path`, ele é ligado ao caminho, sozinho.
2. Se não coincide com nada, ele é o **corpo** da requisição.

Fizemos o experimento no gabarito, com o dev mode no ar:

| Assinatura de `buscar` com `@Path("/{id}")` | `GET /chaves/1` (chave existente) |
|---|---|
| `buscar(@PathParam("id") Long id)` (o gabarito) | 200 |
| `buscar(Long id)`, sem anotação | **200**: o nome `id` bateu com `{id}` |
| `buscar(Long chaveId)`, sem anotação | **500**: o parâmetro virou "corpo" de um `GET`, chegou `null` e estourou no `Map` |

No terceiro caso, o próprio Quarkus avisou no build:

```
WARN  [org.jbo.res.rea.com.pro.EndpointIndexer] (build-7) Using a body parameter with jakarta.ws.rs.GET is strongly discouraged. Offending method is 'tech.ada.pix.chaves.ChavePixResource#tech.ada.pix.chaves.ChavePixResponse buscar(java.lang.Long chaveId)'
```

A ligação pelo nome funciona porque o nome do parâmetro fica no bytecode: o Maven já grava essa informação por padrão (dados de depuração), e o `<parameters>true</parameters>` do `pom.xml` gerado também a grava. O gabarito anota tudo explicitamente, por clareza: quem lê o código não precisa conhecer a regra.

> ✍️ **Experimente agora**
> Repita o experimento no seu projeto. Lembre que, a cada mudança de código, o dev mode reinicia e o `Map` em memória começa vazio: faça um `POST` antes de cada `GET`.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 na regra de ligação de parâmetros.

---

## 5. Modelo de execução: o Quarkus REST não é "só reativo"

O Quarkus REST escolhe a thread pela **assinatura** do método:

| O método devolve | Roda em | Pode bloquear (JDBC, chamada HTTP síncrona)? |
|---|---|---|
| Tipo comum (`record`, `List<T>`, `String`, `RestResponse<T>`...) | **worker thread** | Sim |
| `Uni<T>`, `Multi<T>`, `CompletionStage<T>` | **thread de I/O** (event loop) | Não |

Conferimos com um recurso de diagnóstico (compilado e executado na 3.15.7; não faz parte do gabarito):

```java
@Path("/diagnostico")
public class DiagnosticoResource {

    @GET
    @Path("/thread")
    public String thread() {
        return Thread.currentThread().getName();
    }

    @GET
    @Path("/thread-uni")
    public Uni<String> threadUni() {
        return Uni.createFrom().item(Thread.currentThread().getName());
    }
}
```

```
$ curl localhost:8080/diagnostico/thread
executor-thread-1
$ curl localhost:8080/diagnostico/thread-uni
vert.x-eventloop-thread-0
```

Todo o código deste módulo usa retorno comum, e por isso se comporta como o Spring MVC que você conhece: uma *worker thread* por requisição. Programação reativa fica fora do módulo.

> **Da 3.15 até a 3.40** — Na **3.35**, um endpoint `@Transactional` que retorna `Uni` passa a exigir `@Blocking`. Não afeta o nosso código, que só usa retorno comum.

---

## 6. CDI com ArC: escopos e injeção

No Quarkus a injeção de dependência é **CDI** (Jakarta CDI), implementada pelo **ArC**. Não existe `@Component` nem `@Service`: o papel da classe ("serviço", "repositório") está no **nome**, e o **escopo** está na anotação.

| Escopo | Quantas instâncias | Observação |
|---|---|---|
| `@ApplicationScoped` | Uma para a aplicação, criada na 1ª chamada | Injetada por um **proxy**: permite trocar por *mock* nos testes. Padrão para serviço e repositório |
| `@Singleton` | Uma para a aplicação | Sem proxy: um pouco mais leve, mas não aceita `@InjectMock` (Aula 5) |
| `@RequestScoped` | Uma por requisição HTTP | Morre no fim da requisição |
| `@Dependent` | Uma por ponto de injeção | — |

O serviço e o repositório do gabarito (`gabarito/aula-02/.../ChavePixService.java` e `ChavePixRepository.java`, trechos):

```java
@ApplicationScoped
public class ChavePixService {

    /** Regra do DICT para pessoa física: até 5 chaves por conta. Vira configuração na Aula 4. */
    static final int LIMITE_POR_CONTA = 5;

    @Inject
    ChavePixRepository repository;

    public ChavePix buscar(Long id) {
        return repository.findByIdOptional(id).orElseThrow(() -> new ChaveNaoEncontradaException(id));
    }
    // cadastrar, listar, trocarConta, remover...
}
```

```java
@ApplicationScoped
public class ChavePixRepository {

    private final Map<Long, ChavePix> chaves = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public void persist(ChavePix chave) {
        chave.setId(sequencia.incrementAndGet());
        chaves.put(chave.getId(), chave);
    }

    public Optional<ChavePix> findByIdOptional(Long id) {
        return Optional.ofNullable(chaves.get(id));
    }
    // listAll, findByTitular, countByConta, existeChave, deleteById...
}
```

- **Campo package-private** (`ChavePixRepository repository;`, sem `private`) é o padrão dos guias do Quarkus. Campo `private` também funciona, sem aviso: no build, o ArC o transforma em package-private para injetar sem reflexão (`quarkus.arc.transform-private-injected-fields`, ligada por padrão). O guia de CDI desencoraja membros privados nos beans porque, em construtores, observadores e produtores, eles exigem reflexão. Injeção por **construtor** também funciona e, com um único construtor, o `@Inject` é dispensável. Escolha uma forma e seja consistente.
- **`ConcurrentHashMap`**, porque o repositório `@ApplicationScoped` é compartilhado por requisições concorrentes.
- Os nomes `persist`, `findByIdOptional`, `listAll` e `deleteById` imitam o Panache **de propósito**: na Aula 3 a troca é pequena.

**O escopo errado, na prática.** Trocamos o `@ApplicationScoped` do repositório por `@RequestScoped` e repetimos:

```
POST /chaves      → HTTP/1.1 201 Created   (Location: .../chaves/1)
GET  /chaves/1    → HTTP/1.1 404 Not Found
GET  /chaves      → []
```

Cada requisição ganhou um `Map` novo: a chave criada no `POST` morreu com a requisição.

**O build confere tudo.** O ArC resolve as injeções durante o build. Injeção sem candidato quebra o build, não a primeira requisição. E bean que ninguém injeta é **removido**. Criamos um `AuditoriaDeChaves` `@ApplicationScoped` que ninguém injeta e perguntamos ao contêiner, num endpoint de diagnóstico, se ele existia (`CDI.current().select(AuditoriaDeChaves.class).isResolvable()`):

```
sem @Unremovable  → AuditoriaDeChaves é bean? false
com @Unremovable  → AuditoriaDeChaves é bean? true
```

`@io.quarkus.arc.Unremovable` impede a remoção. Na prática, você só esbarra nisso quando busca um bean pelo contêiner em vez de injetá-lo com `@Inject`.

> 💭 **Pare e reflita**
> No Spring, um `@Service` que ninguém injeta continua vivo no contexto. No Quarkus, ele some no build. Que tipo de código do seu dia a dia depende de beans que "ninguém injeta"?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no CDI, nos escopos e na remoção de beans não usados.

---

## 7. OpenAPI e Swagger UI

Acrescente a extensão (pela Dev UI, aba de extensões, ou pelo plugin):

```bash
mvn quarkus:add-extension -Dextensions=quarkus-smallrye-openapi
```

E dê um título ao contrato (`gabarito/aula-02/chaves-pix/src/main/resources/application.properties`):

```properties
quarkus.smallrye-openapi.info-title=API de Chaves PIX
quarkus.smallrye-openapi.info-version=1.0.0
```

O contrato fica em `/q/openapi` e a Swagger UI em `/q/swagger-ui` (em dev e test; no jar de produção ela não entra por padrão). Um trecho real do contrato gerado:

```yaml
openapi: 3.0.3
info:
  title: API de Chaves PIX
  version: 1.0.0
paths:
  /chaves:
    get:
      tags:
      - Chave Pix Resource
      parameters:
      - name: titular
        in: query
        schema:
          type: string
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: "#/components/schemas/ChavePixResponse"
    post:
      tags:
      - Chave Pix Resource
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/NovaChaveRequest"
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ChavePixResponse"
```

> 💭 **Pare e reflita**
> O contrato diz que o `POST` responde **200**. A API de verdade responde **201**, e também 409 e 422, que nem aparecem. O contrato é gerado do código: que informação o código não deu a ele? Quem vai consumir a API (uma pessoa, outro serviço, um agente de IA lendo o contrato) vai errar onde?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 na extensão de OpenAPI e na Swagger UI.

---

## 8. No Spring era assim

| No Spring Boot | No Quarkus 3.15 |
|---|---|
| `@RestController` + `@RequestMapping("/chaves")` | `@Path("/chaves")` na classe |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | `@GET`, `@POST`, `@PUT`, `@DELETE` (+ `@Path("/{id}")`) |
| `@PathVariable` | `@PathParam("id")`; sem anotação, se o nome bater com `{id}` |
| `@RequestParam` | `@QueryParam("titular")` |
| `@RequestBody` | parâmetro sem anotação (que não bate com variável do caminho) |
| `ResponseEntity<T>` | **não existe**: `RestResponse<T>` ou `Response` |
| `ResponseEntity.created(uri)` | `RestResponse.ResponseBuilder.<T>created(uri).entity(...).build()` |
| Exceção com `@ResponseStatus` | Exceção que estende `ClientErrorException`/`NotFoundException` (até a Aula 6) |
| `@Service`, `@Component`, `@Repository` | `@ApplicationScoped` (o papel fica no nome da classe) |
| `@Autowired` | `@Inject` (campo package-private ou construtor) |
| Contexto montado na subida; bean não usado continua lá | Injeção resolvida no build; bean não usado é removido |
| springdoc em `/v3/api-docs` e `/swagger-ui.html` | `quarkus-smallrye-openapi` em `/q/openapi` e `/q/swagger-ui` |

> **Da 3.15 até a 3.40** — as mudanças desta tabela estão nos quadros das seções 2 e 5.

---

**✍️ Desafio da aula**

1. Tenha o CRUD da aula rodando no seu projeto (copie do `gabarito/aula-02/` se precisar).
2. Escreva os testes que faltam, **um por caminho de erro**: `GET /chaves/999` → 404; `POST` duplicado → 409; sexta chave na mesma conta → 422; `DELETE` inexistente → 404. Rode `mvn verify` e compare a sua contagem de testes com a do gabarito (o `ESTADO.md` diz quantos são).
3. Aprofundamento (opcional): documente no OpenAPI os códigos 201, 404, 409 e 422 com as anotações do MicroProfile OpenAPI (`@APIResponse`) e confira na Swagger UI.

Uma dica para os testes: o `@QuarkusTest` sobe a aplicação uma vez e o `Map` é compartilhado pela suíte. Se dois testes usarem a mesma conta, o limite de 5 de um atrapalha o outro. Veja como o `ChavePixResourceTest` do gabarito gera uma conta nova por teste.

**Para ir além** (guias da versão 3.15)

- Quarkus REST (recursos, parâmetros, respostas, modelo de execução): https://quarkus.io/version/3.15/guides/rest
- Introdução ao CDI no Quarkus: https://quarkus.io/version/3.15/guides/cdi
- Referência do CDI (escopos, beans removidos, `@Unremovable`, membros privados): https://quarkus.io/version/3.15/guides/cdi-reference
- OpenAPI e Swagger UI: https://quarkus.io/version/3.15/guides/openapi-swaggerui

**Para levar ao projeto final**

- O contrato REST de cada serviço do grupo, numa tabela `operação · verbo · URI · sucesso · erros`.
- Recurso → serviço → repositório, com as regras no serviço.
- `@ApplicationScoped` + `@Inject` como padrão; escopo diferente só com justificativa.
- Um teste de sucesso **e** um de erro para cada endpoint.

> **Na próxima aula:** desligue o dev mode e suba de novo. Cadê as chaves que você cadastrou? Onde elas deveriam morar, e quem abre e fecha a transação?
