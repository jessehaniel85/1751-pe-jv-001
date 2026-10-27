# Material do Aluno — Aula 1: do HTTP ao primeiro projeto Quarkus

> **Tempo de leitura:** ~15 min. Você acabou de construir um serviço em Spring Boot. Nas próximas oito aulas, vai construir o mesmo tipo de coisa em **Quarkus**: API REST, banco, configuração, dois serviços conversando, validação e segurança. Hoje o objetivo é curto e inegociável: **gerar o seu projeto pelo plugin Maven, subir o modo de desenvolvimento e ver um teste verde na sua máquina.** HTTP e Maven você já domina; aqui eles aparecem só como revisão rápida, para fixar o contrato que vamos usar no módulo inteiro.
>
> **Pilha do módulo (uma versão de cada coisa):** Java **21** · Quarkus **3.15.7** · Maven pelo repositório corporativo · sem Docker.
>
> As três pausas de sempre: 🎬 **Imagine o cenário**, 💭 **Pare e reflita** e ✍️ **Experimente agora**.

---

## 1. Revisão relâmpago: o contrato HTTP do módulo

Durante o módulo vamos construir a **API de Chaves PIX**, um diretório de chaves simplificado, parecido com o que o Banco Central mantém (o DICT). O contrato dela já diz quase tudo o que você precisa lembrar de HTTP:

| Operação | Verbo e URI | Sucesso | Erros previstos |
|---|---|---|---|
| Cadastrar chave | `POST /chaves` | **201** + cabeçalho `Location` | 400 (pedido malformado), 409 (chave já existe), 422 (conta no limite) |
| Listar chaves | `GET /chaves?titular=<cpf>` | 200 | — |
| Consultar uma chave | `GET /chaves/{id}` | 200 | 404 |
| Trocar a conta vinculada | `PUT /chaves/{id}` | 200 | 404, 422 |
| Remover chave | `DELETE /chaves/{id}` | **204**, sem corpo | 404 |

Três lembretes que vão aparecer de novo:

- **Idempotência.** `GET`, `PUT` e `DELETE` são idempotentes: repetir a mesma requisição deixa o servidor no mesmo estado. `POST` não é: dois `POST` iguais tentam criar duas chaves. Isso importa quando alguém faz *retry* automático (Aula 5).
- **400 × 422.** 400 é pedido que não dá nem para entender (JSON quebrado, campo obrigatório faltando). 422 é pedido bem formado que fere uma regra de negócio, como "a conta já tem 5 chaves".
- **Nível 2 de Richardson**, em uma frase: cada recurso tem a sua URI, o verbo HTTP diz a intenção e o código de status diz o resultado. O nível 3 (HATEOAS) fica fora do módulo.

> 💭 **Pare e reflita**
> Uma chave aleatória é gerada pelo servidor (o cliente não escolhe o valor). Se o `POST` de uma chave aleatória cair por *timeout* e o cliente repetir, quantas chaves podem existir no fim? O que isso diz sobre `POST` e *retry*?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (HTTP e REST não dependem da versão do Quarkus).

---

## 2. Maven e o BOM: quem manda nas versões

Você já usou um `pom.xml` com `spring-boot-starter-parent`. No Quarkus a ideia é parecida, mas o mecanismo é outro: em vez de herdar de um *parent*, o projeto **importa um BOM** (*bill of materials*), um `pom` que só lista versões. Este é o trecho principal do que o plugin gera (copiado de `gabarito/aula-01/chaves-pix/pom.xml`, abreviado: o arquivo também traz `quarkus-arc`, `quarkus-rest` e `quarkus-junit5`):

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.version>3.15.7</quarkus.platform.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>${quarkus.platform.group-id}</groupId>
            <artifactId>${quarkus.platform.artifact-id}</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest-jackson</artifactId>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

O que observar:

- **Nenhuma dependência tem `<version>`.** Quem decide é o BOM 3.15.7, que testou todas as extensões juntas. Pôr uma versão à mão numa extensão é o jeito mais rápido de misturar bibliotecas incompatíveis.
- **Extensão** é o nome que o Quarkus dá ao que o Spring chama de *starter*: `quarkus-rest-jackson` traz o servidor REST e a conversão JSON com Jackson.
- O `quarkus-maven-plugin` vem com `<extensions>true</extensions>`: é ele que faz o trabalho de build que você vai ver na seção 3.
- O `groupId` do REST Assured é `io.rest-assured`, não `io.quarkus`.

**Jackson sem configuração.** Com `quarkus-rest-jackson`, um `record` devolvido por um endpoint vira JSON sozinho, e `LocalDateTime` sai no formato ISO (`"2026-10-28T15:00:00"`), como você vai ver na seção 6. As anotações que você conhece (`@JsonProperty`, `@JsonIgnore`, `@JsonFormat`, `@JsonCreator`) continuam valendo.

**Lombok:** não usamos neste módulo; `record` e as entidades do Panache (Aula 3) resolvem quase todo o código repetitivo. Se usar em outro projeto com Java 21, a versão mínima é a 1.18.30 (as anteriores não compilam com o JDK 21).

**Na rede corporativa:** use sempre `mvn` (o Maven instalado), com o `settings.xml` do repositório corporativo (`-s <caminho>`, conforme o checklist de ambiente). O `./mvnw` que o plugin gera tenta baixar o Maven da internet e falha.

> ✍️ **Experimente agora**
> No seu projeto (depois da seção 4), rode `mvn dependency:tree -Dincludes=com.fasterxml.jackson.core` e ache a linha do `jackson-databind`. Que versão o BOM escolheu? Você escreveu essa versão em algum lugar?

> **Da 3.15 até a 3.40** — Na **3.16** deixam de resolver os nomes antigos da renomeação da 3.9 (`quarkus-resteasy-reactive*`, `quarkus-rest-client-reactive*`); na 3.15 eles ainda funcionam por redirecionamento, mas um `pom.xml` copiado de tutorial antigo quebra nas versões novas. Use só os nomes `quarkus-rest*`. O BOM continua sendo importado do mesmo jeito.

---

## 3. A ideia que amarra o módulo: o Quarkus decide no build

> 🎬 **Imagine o cenário**
> Um serviço de consulta de agências roda num servidor de aplicação. No dia de pagamento de benefícios a procura multiplica, e a plataforma sobe réplicas novas automaticamente. Só que cada réplica leva minutos para ficar pronta, e o pico passa antes de ela ajudar. O que uma aplicação Java faz nesses minutos? E o que daria para fazer **antes** de ela rodar?

| | Spring Boot | Quarkus |
|---|---|---|
| Quando a aplicação é "montada" | Na **subida**: varre o classpath, avalia `@ConditionalOn...`, cria proxies | No **build**: lê as anotações, resolve a injeção de dependência e gera o código de inicialização |
| O que isso dá | Flexibilidade na subida | Subida rápida e menos memória, mesmo na JVM; base para imagem nativa (Aula 5) |
| O que isso cobra | Subida mais lenta | Parte da configuração fica fixa no build; erros de injeção aparecem no build |

Consequências que você vai sentir já nas próximas aulas:

- **Não existe classe `main` nem `@SpringBootApplication`.** O Quarkus gera a inicialização.
- **Bean que ninguém injeta pode ser removido no build** (Aula 2).
- **Algumas propriedades só têm efeito no build**: mudar depois de empacotar não adianta (Aula 4).

> 💭 **Pare e reflita**
> No serviço Spring do módulo anterior, quanto tempo levava entre o `main` e a linha `Started ... in X seconds`? O que acontecia nesse intervalo?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no modelo de build.

---

## 4. Gerando o projeto pelo plugin Maven

Não usamos a Quarkus CLI nem o site code.quarkus.io: o plugin Maven faz o mesmo e baixa tudo pelo repositório corporativo. Dentro da pasta `inicio/` do projeto guiado (que só tem o `pom.xml` agregador), rode:

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.15.7:create \
    -DprojectGroupId=tech.ada.pix \
    -DprojectArtifactId=chaves-pix \
    -DpackageName=tech.ada.pix.chaves \
    -Dextensions='quarkus-rest-jackson' \
    -DjavaVersion=21
```

No Windows (PowerShell ou cmd), escreva tudo numa linha só e use aspas duplas: `-Dextensions="quarkus-rest-jackson"`.

O fim da saída (execução real com a 3.15.7):

```
applying codestarts...
[INFO] 📚 java
🔨 maven
📦 quarkus
📝 config-properties
🔧 tooling-dockerfiles
🔧 tooling-maven-wrapper
🚀 rest-codestart
-----------
[SUCCESS] ✅  quarkus project has been successfully generated in:
--> .../chaves-pix
-----------
...
[INFO] BUILD SUCCESS
```

O que nasceu:

```
inicio/
├── pom.xml                 ← o plugin acrescentou <module>chaves-pix</module>
└── chaves-pix/
    ├── pom.xml             ← o BOM 3.15.7 está aqui
    ├── mvnw, mvnw.cmd, .mvn/          → apagar (wrapper baixa da internet)
    ├── .dockerignore, src/main/docker/ → apagar (sem Docker no curso)
    ├── README.md
    └── src/
        ├── main/java/tech/ada/pix/chaves/GreetingResource.java   (GET /hello)
        ├── main/resources/application.properties                 (vazio)
        └── test/java/tech/ada/pix/chaves/
            ├── GreetingResourceTest.java
            └── GreetingResourceIT.java
```

O `GreetingResource` e os dois testes de exemplo são úteis para ver funcionando; depois, apague e siga com o código da seção 6.

> ✍️ **Experimente agora**
> Rode `mvn verify` dentro de `chaves-pix`. No fim, procure a linha `Tests run:`. Quantos testes rodaram? E procure a linha `Installed features:` no meio do log: que extensões aparecem que você não pediu?

> **Da 3.15 até a 3.40** — O comando de criação é o mesmo nas versões novas, trocando o número da versão. Na **3.31**, `quarkus-junit5` passa a se chamar `quarkus-junit` (o nome antigo continua resolvendo por redirecionamento). O Quarkus 4 está em beta desde 01/10/2026 e fica fora deste módulo.

---

## 5. Modo de desenvolvimento e Dev UI

```bash
cd chaves-pix
mvn quarkus:dev
```

A subida, copiada de uma execução real:

```
Listening for transport dt_socket at address: 5005
Tests paused
Press [e] to edit command line args (currently ''), [r] to resume testing, [o] Toggle test output, [h] for more options>
... chaves-pix 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.15.7) started in 1.276s. Listening on: http://localhost:8080
... Profile dev activated. Live Coding activated.
... Installed features: [cdi, rest, rest-jackson, smallrye-context-propagation, vertx]
```

Repare em três coisas: o depurador já está aberto na porta **5005**; os testes estão **pausados**, esperando a tecla `r`; e o perfil ativo é **dev**.

**Live reload: recompila na próxima requisição.** Salvar um arquivo não reinicia nada. Quando chega a próxima chamada HTTP, o Quarkus percebe a mudança, recompila e reinicia em menos de um segundo. Foi o que aconteceu quando acrescentamos as classes da seção 6 com o dev mode no ar e chamamos `GET /chaves`:

```
... Restarting quarkus due to changes in ChavePixResponse.class, ChavePixResource.class, TipoChave.class.
... chaves-pix stopped in 0.006s
... chaves-pix 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.15.7) started in 0.290s. Listening on: http://localhost:8080
... Live reload total time: 0.567s
```

**Teclas do console** (guia de testes contínuos da 3.15): `r` roda os testes e liga o teste contínuo; `f` roda só os que falharam; `o` mostra a saída dos testes; `s` força o reinício; `h` mostra a ajuda; `q` encerra.

**Dev UI.** Em dev mode, `http://localhost:8080/` redireciona para `/q/dev-ui/welcome`. O menu da Dev UI traz, entre outros: **Extensions**, editor de configuração (**Form Editor** e **Source Editor**), **Endpoints**, **Continuous Testing**, **Dev services** e **Application Dependencies**. Ela só existe no dev mode: no jar de produção, `/q/dev-ui` responde 404.

**Uma pegadinha já na primeira chamada.** O `GreetingResource` gerado devolve `String` com `@Produces(MediaType.TEXT_PLAIN)`. Peça JSON e veja:

```
$ curl -i -H 'Accept: application/json' localhost:8080/hello
HTTP/1.1 406 Not Acceptable
content-length: 0
```

`406` quer dizer "não sei responder no formato que você pediu". Endpoint que devolve um `record` sai em JSON; endpoint que devolve `String` sai como texto.

> ✍️ **Experimente agora**
> Com o dev mode no ar, mude o texto do `GreetingResource`, salve e **não** reinicie nada. Chame `curl localhost:8080/hello`. Em que momento a mudança entrou: quando você salvou ou quando chamou?

> **Da 3.15 até a 3.40** — Na **3.38** entra um Dev Service de observabilidade (Grafana LGTM) no dev mode, que precisa de contêiner; sem Docker, ele não sobe (a partir da Aula 3 nosso projeto já desliga os Dev Services com `quarkus.devservices.enabled=false`). Dev mode e Dev UI: nenhuma outra mudança registrada.

---

## 6. O primeiro recurso: `GET /chaves`

Três classes, todas copiadas de `gabarito/aula-01/chaves-pix/src/main/java/tech/ada/pix/chaves/`.

`TipoChave.java`:

```java
public enum TipoChave {
    CPF,
    EMAIL,
    TELEFONE,
    ALEATORIA
}
```

`ChavePixResponse.java` (o que a API devolve):

```java
public record ChavePixResponse(
        Long id,
        TipoChave tipo,
        String valor,
        String agencia,
        String numeroConta,
        String titularCpf,
        LocalDateTime criadaEm) {
}
```

`ChavePixResource.java`:

```java
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    private static final LocalDateTime CRIACAO = LocalDateTime.of(2026, 10, 28, 15, 0);

    private static final List<ChavePixResponse> CHAVES = List.of(
            new ChavePixResponse(1L, TipoChave.CPF, "52998224725", "0001", "123456", "52998224725", CRIACAO),
            new ChavePixResponse(2L, TipoChave.EMAIL, "maria@exemplo.com.br", "0001", "123456", "52998224725", CRIACAO),
            new ChavePixResponse(3L, TipoChave.TELEFONE, "+5561988887777", "0002", "654321", "11144477735", CRIACAO));

    @GET
    public List<ChavePixResponse> listar() {
        return CHAVES;
    }
}
```

`@Path` e `@GET` são **Jakarta REST** (`jakarta.ws.rs`), a mesma especificação do JAX-RS que muita gente usou em servidor de aplicação. A resposta real:

```
$ curl -i localhost:8080/chaves
HTTP/1.1 200 OK
content-length: 452
Content-Type: application/json;charset=UTF-8

[{"id":1,"tipo":"CPF","valor":"52998224725","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725","criadaEm":"2026-10-28T15:00:00"},{"id":2,"tipo":"EMAIL","valor":"maria@exemplo.com.br","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725","criadaEm":"2026-10-28T15:00:00"},{"id":3,"tipo":"TELEFONE","valor":"+5561988887777","agencia":"0002","numeroConta":"654321","titularCpf":"11144477735","criadaEm":"2026-10-28T15:00:00"}]
```

> 💭 **Pare e reflita**
> A lista é fixa, em memória, e o recurso faz tudo sozinho. O que acontece com esse desenho quando aparecer o `POST`, a regra de duplicidade e o limite por conta? É o problema da Aula 2.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 nas anotações de Jakarta REST usadas aqui.

---

## 7. O primeiro `@QuarkusTest`

`ChavePixResourceTest.java` (de `gabarito/aula-01/chaves-pix/src/test/java/tech/ada/pix/chaves/`):

```java
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
```

- `@QuarkusTest` sobe a aplicação **uma vez** para a suíte inteira e a deixa no ar entre os testes.
- O REST Assured já vem apontado para ela, na porta **8081** (a 8080 fica livre para o seu dev mode).
- Não é teste de fatia: é a aplicação de verdade respondendo HTTP.

`mvn verify`, saída real:

```
... chaves-pix 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.15.7) started in 1.272s. Listening on: http://localhost:8081
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.471 s -- in tech.ada.pix.chaves.ChavePixResourceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

E quando o teste falha (trocamos `hasSize(3)` por `hasSize(4)` de propósito):

```
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
java.lang.AssertionError:
1 expectation failed.
JSON path $ doesn't match.
Expected: a collection with size <4>
  Actual: <[{id=1, tipo=CPF, ...}, {id=2, tipo=EMAIL, ...}, {id=3, tipo=TELEFONE, ...}]>
```

**Teste contínuo:** com o dev mode no ar, aperte `r`. A partir daí, cada vez que você salva, o Quarkus roda de novo os testes afetados, sem sair do dev mode.

**Se a porta 8081 estiver ocupada**, o teste não sobe (`Port(s) already bound: 8081`). A partir da Aula 5 teremos um segundo serviço justamente na 8081; por isso o projeto passa a usar `quarkus.http.test-port=0` (porta aleatória nos testes).

> ✍️ **Experimente agora**
> Quebre o seu teste de propósito, veja o vermelho no teste contínuo (tecla `r`) e volte ao verde. Leia a mensagem de erro do REST Assured: ela diz o que esperava e o que veio?

> **Da 3.15 até a 3.40** — Na **3.31**, a dependência `quarkus-junit5` passa a se chamar `quarkus-junit` (com redirecionamento; o nome antigo continua resolvendo). A anotação `@QuarkusTest` e o REST Assured não mudam.

---

## 8. Empacotar e rodar: não é um *fat jar*

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

O `package` gera uma **pasta**, não um jar único:

```
target/quarkus-app/
├── app/
├── lib/
├── quarkus/
├── quarkus-app-dependencies.txt
└── quarkus-run.jar
```

A subida real do jar desta aula:

```
... chaves-pix 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.15.7) started in 0.545s. Listening on: http://0.0.0.0:8080
... Profile prod activated.
```

Fora do dev mode, o perfil é **prod**. E o `quarkus-run.jar` depende das pastas ao lado dele. Copiado sozinho para outra pasta:

```
$ java -jar quarkus-run.jar
Error: Could not find or load main class io.quarkus.bootstrap.runner.QuarkusEntryPoint
Caused by: java.lang.ClassNotFoundException: io.quarkus.bootstrap.runner.QuarkusEntryPoint
```

Para distribuir, leve a pasta `quarkus-app/` inteira.

> ✍️ **Experimente agora** (opcional)
> Anote o tempo da linha `started in` do seu jar e compare com a linha `Started ... in X seconds` do serviço Spring Boot do módulo anterior, na mesma máquina. Os dois projetos não fazem a mesma coisa, então é uma medida grosseira; a Aula 5 volta ao assunto.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no empacotamento *fast-jar*.

---

## 9. No Spring era assim

| No Spring Boot | No Quarkus 3.15 |
|---|---|
| start.spring.io | `quarkus-maven-plugin:3.15.7:create`, pelo repositório corporativo |
| *Starter* (`spring-boot-starter-web`) | **Extensão** (`quarkus-rest-jackson`), sem versão |
| `spring-boot-starter-parent` como *parent* | BOM importado em `dependencyManagement` |
| Classe com `@SpringBootApplication` e `main` | Não existe: o build gera a inicialização |
| `spring-boot-devtools` (reinicia ao mudar o classpath) | `mvn quarkus:dev` (recompila na próxima requisição) + Dev UI |
| `@RestController` + `@GetMapping` | `@Path` + `@GET` (Jakarta REST) |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` | `@QuarkusTest` + REST Assured, porta 8081 |
| Jar executável único | Pasta `target/quarkus-app/` |
| Actuator em `/actuator/*` | Endpoints do Quarkus em `/q/*` (Aula 4) |

> **Da 3.15 até a 3.40** — as mudanças desta tabela estão nos quadros das seções 2, 4 e 7.

---

**✍️ Desafio da aula**

1. Gere o **seu** projeto pelo plugin (pode ser fora do agregador), suba `mvn quarkus:dev`, chame um endpoint com `curl` e abra a Dev UI.
2. Crie `GET /chaves/total`, que devolve a quantidade de chaves da lista fixa, com um `@QuarkusTest`. Rode `mvn verify` e leia a contagem de testes no fim. Pense no formato da resposta: o que muda no `Content-Type` se o método devolver `int`, `String` ou um `record`? E se ele for declarado com `@Produces(MediaType.TEXT_PLAIN)` e o cliente pedir `Accept: application/json`?
3. Opcional: abra `target/quarkus-app/` e explique, com as suas palavras, por que não é um *fat jar*.

**Para ir além** (guias da versão 3.15)

- Primeiro projeto e dev mode: https://quarkus.io/version/3.15/guides/getting-started
- Plugin Maven (criar projeto, `quarkus:dev`, extensões, empacotamento): https://quarkus.io/version/3.15/guides/maven-tooling
- Testes com `@QuarkusTest` e porta de teste: https://quarkus.io/version/3.15/guides/getting-started-testing
- Teste contínuo e teclas do console: https://quarkus.io/version/3.15/guides/continuous-testing

**Para levar ao projeto final**

- Cada serviço do grupo nasce pelo plugin Maven 3.15.7, dentro de um `pom.xml` agregador, como o projeto guiado.
- Extensões sem `<version>`; só nomes `quarkus-rest*`.
- Apague o Maven Wrapper e os Dockerfiles gerados; use `mvn`.
- Desde o primeiro endpoint, um `@QuarkusTest` que prova o comportamento.

> **Na próxima aula:** o CRUD completo das chaves, em camadas, com o código HTTP certo em cada caminho. E uma pergunta para chegar com palpite: onde ficam o `@Service`, o `@Autowired` e o `ResponseEntity` no Quarkus? Um deles não existe.
