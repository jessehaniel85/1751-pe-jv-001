# Material do Aluno — Aula 8: Autorização (papel, posse, CORS e CSRF)

> **Segunda, 16/11/2026 · Tempo de leitura:** ~15 min. Na Aula 7 a API aprendeu **quem** está chamando. Hoje ela decide **o que** cada um pode fazer. São duas perguntas diferentes, e cada uma mora num lugar: "o seu papel permite esta operação?" fica no recurso; "este dado é seu?" fica no service. No fim, dois temas que todo mundo confunde com autorização: CORS e CSRF. É também a última aula antes da banca.

---

## 1. Papel não é posse

🎬 **Imagine o cenário.** Um sistema de contestação de compras no cartão de crédito. O cliente abre uma contestação de uma compra que não reconhece; o analista vê todas e pede documentos; o supervisor aprova ou recusa o estorno; a auditoria só lê. Um dia, um cliente descobre que, trocando o número da contestação na URL, vê a contestação de outra pessoa. Na mesma semana, um analista aprova um estorno que só o supervisor poderia aprovar.

São dois defeitos de natureza diferente:

| Defeito | A regra depende de... | Onde a regra fica |
|---|---|---|
| analista aprovando estorno | só do **papel** de quem chama | no recurso, por anotação (`@RolesAllowed`) |
| cliente vendo a contestação de outro | do **dado**: de quem é esta contestação? | no service, depois de buscar o dado |

Todos os clientes têm o mesmo papel. Por isso "o cliente só vê a própria contestação" **não cabe** num `@RolesAllowed`: o papel não sabe de quem é o dado.

No projeto guiado, a matriz do gabarito (`gabarito/aula-08/ESTADO.md`) é esta:

| Operação | titular (username = CPF) | atendente | auditor |
|---|---|---|---|
| `POST /chaves` | só para o próprio CPF | 403 | 403 |
| `GET /chaves` | só as próprias; `?titular=` com outro CPF → `403` | todas | todas |
| `GET /chaves/{id}` | só as próprias | qualquer | qualquer |
| `PUT /chaves/{id}` | só as próprias | 403 | 403 |
| `DELETE /chaves/{id}` | só as próprias | qualquer (a pedido) | 403 |
| `GET /chaves/tipos` | público | público | público |

💭 **Pare e reflita.** Quando o titular pede a chave de outro titular, a API deve responder `403` (você não pode) ou `404` (não existe, para você)? O `404` não revela que o recurso existe; o `403` é mais claro para quem errou de boa-fé. O gabarito usa `403`. Qual você usaria num sistema de contestação?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (é desenho, não API).

---

## 2. RBAC com JSR-250: nega por padrão, abre por método

As anotações são as da especificação **JSR-250** (`jakarta.annotation.security`): `@RolesAllowed`, `@PermitAll`, `@DenyAll`. Na classe, valem para todos os métodos; no método, sobrescrevem a classe. Os nomes dos papéis ficam em constantes, porque anotação só aceita `String` constante (`gabarito/aula-08/chaves-pix/src/main/java/tech/ada/pix/chaves/Papeis.java`):

```java
public final class Papeis {

    /** Dono das chaves; o username é o CPF. */
    public static final String TITULAR = "titular";
    /** Atendimento do banco: lê qualquer chave e remove a pedido do cliente. */
    public static final String ATENDENTE = "atendente";
    /** Auditoria: só leitura. */
    public static final String AUDITOR = "auditor";

    private Papeis() {
    }
}
```

O recurso nega tudo na classe e abre método a método (`ChavePixResource.java`, trecho):

```java
@DenyAll
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    @POST
    @RolesAllowed(TITULAR)
    @Timed(value = "pix.chaves.cadastro", description = "Tempo de resposta do cadastro de chave")
    public RestResponse<ChavePixResponse> cadastrar(@Valid @NotNull NovaChaveRequest request, @Context UriInfo uriInfo) { ... }

    @GET
    @RolesAllowed({TITULAR, ATENDENTE, AUDITOR})
    public List<ChavePixResponse> listar(@QueryParam("titular") String titularCpf) { ... }

    @GET
    @Path("/tipos")
    @PermitAll
    public List<TipoChave> tipos() { ... }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({TITULAR, ATENDENTE})
    public void remover(@PathParam("id") Long id) { ... }

    // buscar e trocarConta seguem a matriz da seção 1
}
```

(Os corpos dos métodos foram trocados por `{ ... }` para mostrar só as anotações; o arquivo completo está no gabarito.)

Por que `@DenyAll` na classe? Lembre da Aula 7: no Quarkus, endpoint sem anotação fica aberto. Com `@DenyAll` na classe, **um endpoint novo nasce fechado**, e quem o criar precisa decidir quem pode chamá-lo.

✍️ **Experimente agora.** Com os dois serviços do gabarito da Aula 8 no ar (usuários e senhas no `README.md` do gabarito). Saídas reais:

```
$ curl -i localhost:8080/chaves
HTTP/1.1 401 Unauthorized
www-authenticate: basic
content-length: 0

$ curl -i -u auditor:auditor123 -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"ALEATORIA","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8
content-length: 92

{"status":403,"erro":"Acesso negado","mensagens":["Seu papel não permite esta operação"]}

$ curl -i -u auditor:auditor123 -X DELETE localhost:8080/chaves/1
HTTP/1.1 403 Forbidden
{"status":403,"erro":"Acesso negado","mensagens":["Seu papel não permite esta operação"]}

$ curl -i localhost:8080/chaves/tipos
HTTP/1.1 200 OK
["CPF","EMAIL","TELEFONE","ALEATORIA"]
```

`401` sem credencial, `403` com credencial e papel errado, `200` no endpoint público. Repare que o `403` já sai com o `ErroResposta` da Aula 6; a seção 3 mostra como.

Os testes da matriz usam credenciais reais (`AutorizacaoTest.java`, trecho):

```java
@Test
void atendenteEAuditorNaoCriamChave() {
    como("atendente", "atendente123").contentType(ContentType.JSON).body(email(MARIA))
            .when().post("/chaves").then().statusCode(403).body("status", equalTo(403));
    como("auditor", "auditor123").contentType(ContentType.JSON).body(email(MARIA))
            .when().post("/chaves").then().statusCode(403);
}
```

Contagem real do gabarito da Aula 8, estado final do projeto guiado (`mvn verify`, 18/10/2026): **79 testes, 0 falhas** (76 no `chaves-pix`, sendo 16 do `AutorizacaoTest`, + 3 no `contas`).

Para regras mais finas que papel existe `@PermissionsAllowed` (com "s"), já na 3.15; fica como leitura no guia de autorização.

> **Da 3.15 até a 3.40** — Na **3.16** entra o `@AuthorizationPolicy` (aplica por anotação uma política de autorização nomeada). Ele **não existe na 3.15**: se um tutorial novo usar, não compila na nossa pilha. `@RolesAllowed`, `@PermitAll`, `@DenyAll` e `@Authenticated`: sem mudança relevante até a 3.40.

---

## 3. Posse: a regra que depende do dado

A regra "só as próprias chaves" está no `ChavePixService`, que lê quem está logado pelo `SecurityIdentity` (`gabarito/aula-08/chaves-pix/src/main/java/tech/ada/pix/chaves/ChavePixService.java`, trechos):

```java
@Inject
SecurityIdentity identidade;

private static final String CHAVE_DE_OUTRO = "A chave pertence a outro titular";
private static final String LISTA_DE_OUTRO = "Titular só lista as próprias chaves";

/**
 * Titular vê só as próprias chaves: sem filtro, a lista é a dele; filtro com o CPF de outro titular é 403
 * (mesma regra de posse do GET por id). Atendente e auditor veem todas, com ou sem filtro.
 */
public List<ChavePix> listar(String titularCpf) {
    if (titularCpf != null) {
        exigirPosse(titularCpf, LISTA_DE_OUTRO);
    }
    String filtro = ehTitular() ? usuarioLogado() : titularCpf;
    return filtro == null ? repository.listAll() : repository.findByTitular(filtro);
}

public ChavePix buscar(Long id) {
    ChavePix chave = repository.findByIdOptional(id).orElseThrow(() -> new ChaveNaoEncontradaException(id));
    exigirPosse(chave.titularCpf, CHAVE_DE_OUTRO);
    return chave;
}

/** Regra de posse: vale para o papel titular (username = CPF); atendente e auditor passam. */
private void exigirPosse(String titularCpf, String mensagem) {
    if (ehTitular() && !usuarioLogado().equals(titularCpf)) {
        throw new ForbiddenException(mensagem);
    }
}

private boolean ehTitular() {
    return identidade.hasRole(Papeis.TITULAR);
}

private String usuarioLogado() {
    return identidade.getPrincipal().getName();
}
```

Como `trocarConta` e `remover` passam por `buscar(id)`, a posse vale para os dois. O `cadastrar` chama `exigirPosse(request.titularCpf(), CHAVE_DE_OUTRO)`: o titular só cadastra chave no próprio CPF. Somada à regra da Aula 6 (chave CPF = CPF do titular), um titular só registra como chave CPF o próprio CPF.

A exceção é a `io.quarkus.security.ForbiddenException`, a mesma que o Quarkus lança quando o `@RolesAllowed` nega. Um mapper novo no `ExceptionMappers` da Aula 6 dá o **mesmo corpo** aos dois `403`:

```java
/** Aula 8: 403 com o mesmo corpo, tanto do @RolesAllowed quanto da regra de posse do service. */
@ServerExceptionMapper
public RestResponse<ErroResposta> acessoNegado(ForbiddenException e) {
    String mensagem = e.getMessage() == null ? "Seu papel não permite esta operação" : e.getMessage();
    return resposta(ErroResposta.de(403, "Acesso negado", mensagem));
}
```

✍️ **Experimente agora.** A titular `52998224725` tem as chaves de id 1 e 51; a chave 101 é do titular `11144477735` (dados do `import.sql`). Saídas reais:

```
# titular pede a própria lista: recebe só as dela
$ curl -s -u 52998224725:titular123 localhost:8080/chaves
[{"id":1,"tipo":"CPF","valor":"52998224725",...,"titularCpf":"52998224725",...},{"id":51,"tipo":"EMAIL","valor":"maria@exemplo.com.br",...,"titularCpf":"52998224725",...}]

# titular pede a lista filtrando por outro CPF: 403
$ curl -i -u 52998224725:titular123 'localhost:8080/chaves?titular=11144477735'
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8
content-length: 91

{"status":403,"erro":"Acesso negado","mensagens":["Titular só lista as próprias chaves"]}

# titular pede uma chave de outro titular
$ curl -i -u 52998224725:titular123 localhost:8080/chaves/101
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8
content-length: 86

{"status":403,"erro":"Acesso negado","mensagens":["A chave pertence a outro titular"]}

# titular tenta cadastrar chave no CPF de outro
$ curl -i -u 52998224725:titular123 -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"ALEATORIA","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8
content-length: 86

{"status":403,"erro":"Acesso negado","mensagens":["A chave pertence a outro titular"]}

# atendente pede as chaves do outro titular: pode
$ curl -s -u atendente:atendente123 'localhost:8080/chaves?titular=11144477735'
[{"id":101,"tipo":"TELEFONE","valor":"+5561988887777","agencia":"0002","numeroConta":"654321","titularCpf":"11144477735","criadaEm":"2026-10-28T15:00:00"}]
```

(Na primeira saída, os `...` cortam campos repetidos para caber na tela. A primeira lista vem do dev mode, com os dados do `import.sql`; o `403` da lista foi conferido no jar em 18/10/2026.)

💭 **Pare e reflita.** Uma versão anterior do gabarito ignorava, em silêncio, o filtro de outro CPF e devolvia só as chaves do titular. Hoje a lista responde `403`, como a consulta por id. O que se ganha recusando em vez de ignorar? Pense em quem chamou errado de boa-fé e em quem está tentando ver dados alheios (e no log de auditoria).

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 registrada na revisão: `SecurityIdentity` e `ForbiddenException` continuam iguais.

---

## 4. Regras por caminho, na configuração

Nem tudo é recurso seu. Os endpoints `/q/health`, `/q/metrics` e `/q/openapi` são do Quarkus, e você não tem onde pôr `@RolesAllowed` neles. Para isso existem as permissões por caminho no `application.properties` (do gabarito):

```properties
# Aula 8: autorização por configuração para os caminhos que não são recursos JAX-RS (/q/*).
# Health aberto (o orquestrador não tem usuário); métricas só para auditor; documentação para quem tem login.
quarkus.http.auth.permission.health.paths=/q/health,/q/health/*
quarkus.http.auth.permission.health.policy=permit
quarkus.http.auth.policy.somente-auditor.roles-allowed=auditor
quarkus.http.auth.permission.metricas.paths=/q/metrics,/q/metrics/*
quarkus.http.auth.permission.metricas.policy=somente-auditor
quarkus.http.auth.permission.documentacao.paths=/q/openapi,/q/openapi/*,/q/swagger-ui,/q/swagger-ui/*
quarkus.http.auth.permission.documentacao.policy=authenticated
```

As políticas prontas são `permit`, `deny` e `authenticated`; `somente-auditor` é uma política nomeada, criada na linha `quarkus.http.auth.policy.<nome>.roles-allowed`. Saídas reais (só o código HTTP):

| Chamada | Resultado |
|---|---|
| `/q/health/live` sem login | 200 |
| `/q/metrics` sem login | 401 |
| `/q/metrics` como `atendente` | 403 |
| `/q/metrics` como `auditor` | 200 |
| `/q/openapi` sem login | 401 |

**A ordem que confunde:** a regra por caminho é avaliada **antes** da anotação. Por isso `@PermitAll` **não abre** um caminho que a configuração fechou. Fizemos o teste: subimos o gabarito com uma regra `authenticated` para `/chaves/tipos`, que tem `@PermitAll`:

```
$ java -Dquarkus.http.auth.permission.fechado.paths=/chaves/tipos \
       -Dquarkus.http.auth.permission.fechado.policy=authenticated \
       -jar chaves-pix/target/quarkus-app/quarkus-run.jar

$ curl -i localhost:8080/chaves/tipos
HTTP/1.1 401 Unauthorized
www-authenticate: basic
content-length: 0
```

Venceu a configuração. Regra prática: anotação para os recursos da aplicação (fica perto do código); configuração para caminhos que não são seus ou para regras amplas. Se misturar, lembre da ordem.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 registrada na revisão em `quarkus.http.auth.permission.*` e `quarkus.http.auth.policy.*`.

---

## 5. CORS: quem protege é o navegador

🎬 **Imagine o cenário.** O time de front-end sobe a tela de chaves em `http://localhost:3000` e chama a API em `http://localhost:8080`. No `curl` funciona; no navegador, o console mostra um erro de CORS e a tela fica vazia.

**CORS** (*Cross-Origin Resource Sharing*) é uma regra **do navegador**: uma página de uma origem (esquema + host + porta) só lê a resposta de outra origem se essa outra origem autorizar, por cabeçalhos `Access-Control-Allow-*`. Para pedidos que não são simples (um `POST` JSON, por exemplo), o navegador manda antes um **preflight**: um `OPTIONS` perguntando "posso?".

Na 3.15, o gabarito liga o filtro e autoriza uma origem:

```properties
# Aula 8: CORS para um front-end de exemplo em outra origem.
# Na 3.15 a chave é quarkus.http.cors=true; da 3.28 em diante ela foi removida e o nome é quarkus.http.cors.enabled=true.
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:3000
quarkus.http.cors.methods=GET,POST,PUT,DELETE
```

✍️ **Experimente agora.** Simule o preflight do navegador com `curl`. Saídas reais:

```
$ curl -i -X OPTIONS -H 'Origin: http://localhost:3000' -H 'Access-Control-Request-Method: POST' localhost:8080/chaves
HTTP/1.1 200 OK
access-control-allow-credentials: true
access-control-allow-origin: http://localhost:3000
access-control-allow-methods: GET,POST,PUT,DELETE
content-length: 0

$ curl -i -X OPTIONS -H 'Origin: http://evil.example' -H 'Access-Control-Request-Method: POST' localhost:8080/chaves
HTTP/1.1 403 CORS Rejected - Invalid origin
access-control-allow-methods: GET,POST,PUT,DELETE
content-length: 0
```

Repare: o Quarkus 3.15 também recusa (`403`) uma requisição que **traga** o cabeçalho `Origin` de uma origem não autorizada. Mas quem não manda `Origin` nenhum (um `curl` comum, outro serviço) recebe a resposta normalmente:

```
$ curl -i localhost:8080/chaves/tipos
HTTP/1.1 200 OK
```

Por isso **CORS não é autenticação nem autorização**. Ele protege o usuário do navegador contra páginas de outra origem; quem protege a API são a autenticação e a autorização das Aulas 7 e 8.

> **Da 3.15 até a 3.40** — Na **3.19**, `quarkus.http.cors=true` é depreciada em favor de `quarkus.http.cors.enabled=true`. Na **3.28**, a chave antiga é **removida**: se ela ficar no arquivo, o CORS fica desligado **sem aviso**. Ao atualizar um serviço, procure essa linha. (Os demais nomes, como `quarkus.http.cors.origins`, continuam.)

---

## 6. CSRF: um ataque de formulário e cookie

**CSRF** (*Cross-Site Request Forgery*) é o ataque em que uma página maliciosa faz o **navegador da vítima** enviar um pedido para o site atacado, e o navegador anexa **sozinho** a credencial que já guarda para aquele site, tipicamente um **cookie de sessão**. A vítima está logada no internet banking, abre outra aba, e um formulário escondido dispara um `POST` de transferência com o cookie dela.

O risco existe quando a autenticação viaja **automaticamente**: cookie de sessão, formulário HTML, login por formulário. A nossa API é JSON, e o cabeçalho `Authorization: Basic` é montado pelo cliente (um app, outro serviço, um front-end com JavaScript) a cada chamada. Nesse uso, o navegador não tem credencial guardada para anexar sozinho a um pedido vindo de outra página, e o ataque descrito acima não funciona. Uma ressalva: se o login Basic for feito pela janela de login do próprio navegador, ele passa a reenviar a credencial automaticamente, e o risco volta. Mesma lógica para token guardado em cookie.

No Quarkus, a proteção vem da extensão `quarkus-rest-csrf`, pensada para formulários: ela gera um token num cookie e confere se o formulário devolve o mesmo token (o exemplo do texto original do módulo usa templates Qute com o campo `csrf-token`). O filtro faz essa conferência sozinho; não é preciso comparar cookie e campo à mão no endpoint.

**Não acrescente `quarkus-rest-csrf` ao `chaves-pix`.** Fizemos o teste numa cópia do gabarito da Aula 8 com a extensão: todo `POST` JSON passou a responder assim, inclusive com credencial e corpo corretos:

```
$ curl -i -u 12345678909:titular123 -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"ALEATORIA","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
HTTP/1.1 400 Bad Request
content-length: 0
```

Sem corpo e sem mensagem. Por padrão, o filtro exige corpo de formulário. Se um dia o seu sistema tiver telas com formulário e sessão, é lá que a extensão entra, num projeto preparado para isso.

💭 **Pare e reflita.** O Spring Security liga a proteção contra CSRF por padrão; o Quarkus só com a extensão. O que cada escolha supõe sobre o tipo de aplicação que você está construindo?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 registrada na revisão para a `quarkus-rest-csrf`.

---

### No Spring era assim

| Você fazia no Spring Boot | No Quarkus |
|---|---|
| `@EnableMethodSecurity` para ligar anotações | não existe: as anotações já funcionam |
| `@PreAuthorize("hasRole('TITULAR')")` ou `@Secured("ROLE_TITULAR")` | `@RolesAllowed("titular")`, sem o prefixo `ROLE_` |
| `@PreAuthorize("#cpf == authentication.name")` | regra de posse no service com `SecurityIdentity.getPrincipal().getName()` |
| `SecurityFilterChain` com `requestMatchers("/actuator/**")...` | `quarkus.http.auth.permission.<nome>.paths` + `.policy` |
| `hasRole(...)` por caminho | `quarkus.http.auth.policy.<nome>.roles-allowed` |
| `AccessDeniedException` → 403 | `io.quarkus.security.ForbiddenException` → 403 (com mapper para o corpo) |
| `http.cors(...)` + `CorsConfigurationSource` | `quarkus.http.cors=true` + `quarkus.http.cors.origins/methods` (na 3.15) |
| CSRF ligado por padrão | CSRF só com `quarkus-rest-csrf`, para formulários |

---

**Para ir além** (guias oficiais da 3.15)

- Autorização de endpoints (anotações, permissões por caminho, `@PermissionsAllowed`): https://quarkus.io/version/3.15/guides/security-authorize-web-endpoints-reference
- CORS: https://quarkus.io/version/3.15/guides/security-cors
- Prevenção de CSRF: https://quarkus.io/version/3.15/guides/security-csrf-prevention
- Testes de segurança: https://quarkus.io/version/3.15/guides/security-testing

**Para levar ao projeto final** (critério 5 da rubrica e fechamento para a banca)

- Pelo menos 2 papéis com regras diferentes; se o domínio pedir, uma regra que dependa do dado (posse), no service.
- Para cada endpoint: um teste com o papel permitido (sucesso), um com papel não permitido (`403`) e um sem credencial (`401`).
- `/q/health` acessível para o orquestrador; decida e documente quem vê `/q/metrics`.
- **Feche o mapa de evidências:** cada critério de 1 a 6 da rubrica com o teste que o prova, inclusive o caminho de erro, ligado no `AVALIACAO.md`. Rode `mvn verify` e anote a contagem real de testes no README.

> **Na próxima aula:** banca. Leia o `aula-09-aluno.md` e o brief do projeto final antes de quarta.
