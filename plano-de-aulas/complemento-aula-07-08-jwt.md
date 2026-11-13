# Leitura complementar das Aulas 7 e 8 — *Bearer token* com JWT no lugar do Basic

> **Leitura opcional, ~20 min.** Nasceu de uma pergunta feita em aula: "e se, em vez do Basic, a API usasse *bearer token*?". O texto responde com a mesma Agenda de Atendimento do projeto hands-on: o que é um JWT, o que muda na autenticação da Aula 7 e o que **não** muda na autorização da Aula 8.
>
> O código está em duas variações da Agenda, ao lado da pasta digitada em aula (`projetos/projeto-hands-on/`), ambas partindo do estado do fim da Aula 8, no Quarkus 3.15.7 com Java 21:
>
> | Pasta | O que muda | Testes (`mvn test`, 13/11/2026) |
> |---|---|---|
> | `agenda-atendimento` | a digitada em aula, com Basic | 51 |
> | `agenda-atendimento-jwt` | a própria Agenda emite e confere o token (seções 3 a 8) | 59, 0 falhas |
> | `agenda-atendimento-keycloak` | um Keycloak emite o token, a Agenda só confere (seção 9); o Keycloak sobe por Docker | 51, 0 falhas |
>
> As saídas mostradas são de execuções reais. Os testes das duas variações rodam **sem Docker**.
>
> As pausas de sempre: 🎬 **Imagine o cenário** e 💭 **Pare e reflita**.

---

## 1. O que incomoda no Basic

🎬 **Imagine o cenário.** O aplicativo do banco guarda o CPF e a senha do cliente para montar o cabeçalho `Authorization: Basic` de cada chamada. Num dia comum, são dezenas de chamadas por cliente, e a senha viaja em todas. Se alguém captura **uma** requisição, tem a senha, que não vence nunca e vale para tudo o que o cliente pode fazer.

Com Basic, a API da Aula 7 recebe usuário e senha **em toda requisição** e confere os dois **em toda requisição**, e a conferência da senha passa pelo BCrypt, que é lento de propósito. Medimos na mesma aplicação, com os dois mecanismos ligados, 20 chamadas seguidas a `GET /agencias/0001/agendamentos` como atendente (máquina local, Apple M1 Pro; duas rodadas):

| Credencial enviada | Tempo médio por chamada |
|---|---|
| `Authorization: Basic ...` (senha conferida com BCrypt) | 88 ms e 92 ms |
| `Authorization: Bearer ...` (assinatura do token conferida) | 2,8 ms e 2,8 ms |

A ideia do *bearer token* é separar os dois momentos:

```
1. login (uma vez)        POST /login {login, senha} ──► confere a senha (BCrypt) ──► devolve um TOKEN
2. uso (muitas vezes)     Authorization: Bearer <TOKEN> ──► confere a assinatura e a validade do token
```

- A senha viaja **uma vez**, só no login.
- O que viaja nas outras chamadas é o **token**: uma credencial temporária, que vence sozinha.
- *Bearer* quer dizer "portador": **quem apresenta o token entra**, sem provar mais nada. Por isso token também só faz sentido com TLS (HTTPS), como o Basic.

No Quarkus, as três peças da Aula 7 continuam as mesmas. Só mudam as duas primeiras:

```
requisição ──► mecanismo de        ──► provedor de           ──► SecurityIdentity
               autenticação            identidade                (nome e papéis)
               (Bearer: lê o token     (JWT: confere assinatura,
               do cabeçalho            emissor e validade;
               Authorization)          nome e papéis vêm do token)
```

💭 **Pare e reflita.** No Basic, quem prova que o atendente é atendente é a tabela `Usuario`, consultada a cada chamada. No token, quem prova?

---

## 2. O que há dentro de um JWT

**JWT** (*JSON Web Token*, RFC 7519) é um formato de token. Um JWT assinado tem três partes separadas por ponto, cada uma em Base64URL.

```
eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1cG4iOiI5Nzc0NDE4MDAwMyIsImdyb3VwcyI6WyJ1c3VhcmlvIl0sImlhdCI6MTc5NDYwNTc5NSwiZXhwIjoxNzk0NjA2MDk1LCJqdGkiOiJmOTY0OWI4ZS0xZjUxLTRkNjMtOWM1NC1iYWNjM2M0ZDU4NjgiLCJpc3MiOiJhZ2VuZGEtYXRlbmRpbWVudG8ifQ.JNpLiIrb8b3wkIGNKJK2oeq_ZwySUFlwBSUinyk6y5Ur...
└────────── cabeçalho ──────────────┘ └──────────────────────────────── claims ─────────────────────────────────...┘ └──── assinatura ────...
```

Esse é um token real, emitido pela `agenda-atendimento-jwt` para a cliente `97744180003` (a assinatura foi cortada aqui). Decodificando as duas primeiras partes:

```json
{"typ":"JWT","alg":"RS256"}
```

```json
{"upn":"97744180003","groups":["usuario"],"iat":1794605795,"exp":1794606095,
 "jti":"f9649b8e-1f51-4d63-9c54-bacc3c4d5868","iss":"agenda-atendimento"}
```

Cada campo do segundo JSON é uma ***claim*** (uma afirmação sobre quem porta o token):

| Claim | Significa | Na Agenda |
|---|---|---|
| `upn` | nome do usuário (*user principal name*) | vira o nome do `SecurityIdentity`: o login, que para o cliente é o CPF |
| `groups` | grupos do usuário | viram os **papéis** que o `@RolesAllowed` confere |
| `iss` | emissor (*issuer*): quem assinou | `agenda-atendimento`; token de outro emissor é recusado |
| `iat` | emitido em (*issued at*), em segundos desde 1970 | preenchido pela biblioteca |
| `exp` | vence em (*expiration*) | `iat` + 300 segundos |
| `jti` | identificador único do token | preenchido pela biblioteca |

A terceira parte é a **assinatura** das duas primeiras. Aqui o algoritmo é o `RS256` (RSA com SHA-256), o padrão da biblioteca: quem **emite** assina com a **chave privada**; quem **recebe** confere com a **chave pública**. Quem tem só a chave pública consegue conferir, mas não consegue fabricar um token.

Duas consequências que valem para todo JWT assinado:

1. **Assinado não é cifrado.** Qualquer um que tenha o token lê as claims, como acabamos de fazer. Senha, dado sigiloso e dado pessoal além do necessário **não** vão no token.
2. **Ninguém altera sem a chave privada.** Trocar `"usuario"` por `"gerente"` nas claims invalida a assinatura (seção 6).

Um detalhe de quem decodifica na mão: Base64**URL** troca `+` e `/` por `-` e `_` e, no JWT, vai **sem o `=` do final**. O `base64 -d` comum pode cortar o fim. Saída real, com outro token da mesma cliente (repare no `jti` diferente):

```
$ echo "$TOKEN" | cut -d. -f2 | base64 -d
{"upn":"97744180003","groups":["usuario"],"iat":1794605795,"exp":1794606095,"jti":"67721783-4d26-4404-b66a-b56cddda8127","iss":"agenda-atendimento"
```

Faltou o `}`. Em Java, `Base64.getUrlDecoder()` lê certo (é o que o `AutenticacaoTest` usa).

💭 **Pare e reflita.** O `upn` do cliente é o CPF dele, e o token passa por *logs* de *proxy*, histórico de terminal e ferramentas de depuração. Que outro identificador poderia ir no token no lugar do CPF?

---

## 3. O que muda na Agenda

| Peça | Com Basic (Aulas 7 e 8) | Com Bearer e JWT |
|---|---|---|
| Extensões | `quarkus-security-jpa` | a mesma, **mais** `quarkus-smallrye-jwt` e `quarkus-smallrye-jwt-build` |
| `application.properties` | `quarkus.http.auth.basic=true` | sai o Basic; entram chave pública, emissor, chave privada e validade |
| Login | não existe: a senha vai em toda chamada | `POST /login` devolve o token |
| `Usuario`, `CargaUsuarios`, `Papel` | — | **iguais** |
| `@DenyAll`, `@RolesAllowed`, `@PermitAll` nos recursos | — | **iguais** |
| Regra de posse no `AgendamentoService` (`SecurityIdentity`) | — | **igual** |
| Regras por caminho do `/q/*` | — | **iguais** (a seção 4 mostra como abrir a documentação em dev) |
| Testes | `given().auth().preemptive().basic(...)` | fazem login e mandam `given().auth().oauth2(token)` |

A autorização da Aula 8 **não muda uma linha**. Ela depende do `SecurityIdentity`, não de como ele foi montado.

### 3.1. Extensões

```xml
<!-- confere o token que chega (Bearer) -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt</artifactId>
</dependency>
<!-- monta e assina o token no POST /login -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt-build</artifactId>
</dependency>
```

A `quarkus-security-jpa` continua no `pom.xml`: a entidade `Usuario` usa as anotações dela, e o `BcryptUtil` vem junto.

### 3.2. O par de chaves

Os três comandos são os do guia do Quarkus (o Git Bash traz o `openssl`). Rode na raiz do projeto:

```bash
mkdir chaves && cd chaves
openssl genrsa -out rsaPrivateKey.pem 2048
openssl rsa -pubout -in rsaPrivateKey.pem -out publicKey.pem
openssl pkcs8 -topk8 -nocrypt -inform pem -in rsaPrivateKey.pem -outform pem -out privateKey.pem
rm rsaPrivateKey.pem
cd ..
```

Ficam dois arquivos: `publicKey.pem` (pode circular) e `privateKey.pem` (**segredo**: quem a tiver fabrica token de gerente). Por isso a pasta entra no `.gitignore`:

```
# par de chaves gerado na máquina; a chave privada nunca vai para o repositório
chaves/
```

### 3.3. Configuração

No `application.properties`, **sai** a linha `quarkus.http.auth.basic=true` e entram estas:

```properties
# Quem confere o token: chave pública e emissor esperado.
mp.jwt.verify.publickey.location=chaves/publicKey.pem
mp.jwt.verify.issuer=agenda-atendimento
# Quem emite o token (POST /login): chave privada, emissor e validade em segundos (300 é o padrão da biblioteca).
# Em produção, os dois caminhos vêm por variável de ambiente:
# MP_JWT_VERIFY_PUBLICKEY_LOCATION e SMALLRYE_JWT_SIGN_KEY_LOCATION.
smallrye.jwt.sign.key.location=chaves/privateKey.pem
smallrye.jwt.new-token.issuer=${mp.jwt.verify.issuer}
smallrye.jwt.new-token.lifespan=300
```

As regras por caminho da Aula 8 (`quarkus.http.auth.permission.*`) ficam como estão.

O caminho `chaves/...` é relativo à pasta de onde a aplicação é iniciada. Rodamos o jar a partir de **outra** pasta, sem as chaves ao lado: a aplicação sobe e **toda** requisição responde 500, inclusive o `/q/health/live`. No *log*:

```
SRJWT02002: Failed to read the public key content from 'mp.jwt.verify.publickey.location'
Caused by: java.net.MalformedURLException: no protocol: chaves/publicKey.pem
```

Com as variáveis `MP_JWT_VERIFY_PUBLICKEY_LOCATION` e `SMALLRYE_JWT_SIGN_KEY_LOCATION` apontando para o caminho completo de cada arquivo, o mesmo jar, iniciado dessa mesma pasta sem as chaves, emitiu e aceitou tokens.

### 3.4. O login

Entrada e saída (`LoginRequest.java` e `TokenResponse.java`):

```java
public record LoginRequest(@NotBlank String login, @NotBlank String senha) {

}
```

```java
public record TokenResponse(String token, String tipo, long expiraEmSegundos) {

}
```

O serviço confere a senha e monta o token (`TokenService.java`):

```java
@ApplicationScoped
public class TokenService {

  private final UsuarioRepository usuarios;
  private final long validadeEmSegundos;

  public TokenService(UsuarioRepository usuarios,
    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan") long validadeEmSegundos) {
    this.usuarios = usuarios;
    this.validadeEmSegundos = validadeEmSegundos;
  }

  /** Troca login e senha por um token. Usuário inexistente e senha errada dão a mesma resposta, de propósito. */
  public TokenResponse emitir(String login, String senha) {
    Usuario usuario = this.usuarios.findByLogin(login)
      .filter(encontrado -> BcryptUtil.matches(senha, encontrado.getSenha()))
      .orElseThrow(CredencialInvalidaException::new);
    String token = Jwt.upn(usuario.getLogin())                // quem é: vira o nome do SecurityIdentity
      .groups(Set.of(usuario.getPapeis().split(",")))         // o que pode: vira os papéis do @RolesAllowed
      .sign();                                                // iss e validade: configuração; iat e jti: biblioteca
    return new TokenResponse(token, "Bearer", this.validadeEmSegundos);
  }

}
```

- Imports menos óbvios: `io.smallrye.jwt.build.Jwt`, `io.quarkus.elytron.security.common.BcryptUtil` e `org.eclipse.microprofile.config.inject.ConfigProperty`. O `sign()` sem argumento usa a chave de `smallrye.jwt.sign.key.location`.
- No Basic, quem conferia a senha era o Quarkus, por causa do `@UserDefinition`. Aqui a conferência é nossa: `BcryptUtil.matches(senhaDigitada, hashGuardado)`.
- Usuário inexistente e senha errada lançam a **mesma** exceção, com a mesma mensagem. Responder "usuário não existe" ajudaria quem está tentando adivinhar *logins*. Uma ressalva: o BCrypt só é calculado quando o usuário existe, então o **tempo** da resposta ainda pode denunciar a diferença (não medimos).

A exceção e o *mapper*, no molde da Aula 6 (`CredencialInvalidaException.java` e o método novo em `ExceptionMappers.java`):

```java
public class CredencialInvalidaException extends RuntimeException {

  public CredencialInvalidaException() {
    super("Login ou senha inválidos");
  }
}
```

```java
  @ServerExceptionMapper
  public RestResponse<ErroResposta> credencialInvalida(CredencialInvalidaException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.UNAUTHORIZED.code(), "Não autenticado", e.getMessage()));
  }
```

E o recurso (`LoginResource.java`):

```java
@Path("/login")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoginResource {

  private final TokenService service;
  private final JsonWebToken jwt;

  public LoginResource(TokenService service, JsonWebToken jwt) {
    this.service = service;
    this.jwt = jwt;
  }

  // Aberto: é aqui que quem ainda não tem token consegue um.
  @PermitAll
  @POST
  public TokenResponse entrar(@Valid @NotNull LoginRequest request) {
    return this.service.emitir(request.login(), request.senha());
  }

  // Lê as claims do token da requisição corrente.
  @Authenticated
  @GET
  @Path("/eu")
  public QuemSouEuResponse quemSouEu() {
    return new QuemSouEuResponse(this.jwt.getName(), this.jwt.getGroups(), this.jwt.getIssuer(),
      Instant.ofEpochSecond(this.jwt.getExpirationTime()));
  }

}
```

```java
public record QuemSouEuResponse(String login, Set<String> papeis, String emissor, Instant expiraEm) {

}
```

`JsonWebToken` (`org.eclipse.microprofile.jwt.JsonWebToken`) é o token da requisição corrente, injetável como o `SecurityIdentity`. Use quando precisar de uma claim que não é nome nem papel. O `@Authenticated` é o da Aula 7 (`io.quarkus.security.Authenticated`). O `GET /login/eu` existe só para mostrar isso, e ajuda a depurar ("com que usuário este token está entrando?").

> **Da 3.15 até a 3.40** — Na 3.15, sem chave configurada não há token. O guia da 3.40 descreve **geração automática do par de chaves em dev e em teste** quando nenhuma chave de verificação é configurada (não conferimos em qual versão isso entrou); para produção, a chave continua sendo sua. O guia da 3.40 também lista `smallrye.jwt.new-token.add-default-claims`, que desliga o preenchimento automático de `iat`, `exp` e `jti`. `mp.jwt.verify.*`, `smallrye.jwt.sign.key.location` e `Jwt.upn(...).groups(...).sign()` aparecem iguais nos dois guias.

---

## 4. Usando a API

Com a `agenda-atendimento-jwt` no ar (`quarkus:dev`), saídas reais:

```
$ curl -i localhost:8080/agendamentos
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer
content-length: 0
```

O desafio agora é `Bearer`, não `basic`. E a credencial da Aula 7 não entra mais:

```
$ curl -i -u 97744180003:senha123 localhost:8080/agendamentos
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer
content-length: 0
```

O login:

```
$ curl -i -X POST localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"login":"97744180003","senha":"errada"}'
HTTP/1.1 401 Unauthorized
Content-Type: application/json;charset=UTF-8

{"status":401,"erro":"Não autenticado","mensagens":["Login ou senha inválidos"]}

$ curl -i -X POST localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"login":"97744180003","senha":"senha123"}'
HTTP/1.1 200 OK
Content-Type: application/json;charset=UTF-8

{"token":"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1cG4i...","tipo":"Bearer","expiraEmSegundos":300}
```

Guarde o token numa variável e use nas chamadas seguintes:

```
$ TOKEN="cole-aqui-o-valor-do-campo-token"

$ curl -i -H "Authorization: Bearer $TOKEN" localhost:8080/agendamentos
HTTP/1.1 200 OK
Content-Type: application/json;charset=UTF-8

[]

$ curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/login/eu
{"login":"97744180003","papeis":["usuario"],"emissor":"agenda-atendimento","expiraEm":"2026-11-13T21:41:35Z"}
```

Com esse token em uso, olhamos o SQL do perfil dev. O `POST /login` faz **uma** consulta à tabela de usuários:

```
select u1_0.id, u1_0.login, u1_0.papeis, u1_0.senha from Usuario u1_0 where u1_0.login=? fetch first ? rows only
```

Três chamadas seguidas a `GET /agencias/0001/agendamentos` com o token de atendente fazem três consultas a `Agendamento` e **nenhuma** a `Usuario`. A API não foi ao banco para saber quem estava chamando: leu do token.

**E a Swagger UI?** A regra da Aula 8 exige login para `/q/openapi` e `/q/swagger-ui`. Com Basic, o navegador abria a janela de usuário e senha. Com Bearer, o navegador não tem como mandar o token ao abrir a página, e ela responde 401. Para usar a Swagger UI em dev com token, são duas linhas (conferidas em execução):

```properties
# Documentação aberta só em dev: o navegador não manda Bearer ao abrir a página.
%dev.quarkus.http.auth.permission.documentacao.policy=permit
# Declara o esquema Bearer no documento OpenAPI; a Swagger UI ganha o botão Authorize para colar o token.
quarkus.smallrye-openapi.security-scheme=jwt
```

Com a segunda linha, o `/q/openapi` passa a trazer:

```yaml
  securitySchemes:
    SecurityScheme:
      type: http
      description: Authentication
      scheme: bearer
      bearerFormat: JWT
```

---

## 5. Autorização: o mesmo código da Aula 8

Com o token da cliente (papel `usuario`), saídas reais:

```
$ curl -i -H "Authorization: Bearer $TOKEN" localhost:8080/agencias/0001/agendamentos
HTTP/1.1 403 Forbidden
Content-Type: application/json;charset=UTF-8

{"status":403,"erro":"Acesso negado","mensagens":["Seu papel não permite esta operação"]}
```

| Chamada com o token da cliente | Resposta | Quem decidiu |
|---|---|---|
| `GET /agencias/0001/agendamentos` | 403 | `@RolesAllowed({ATENDENTE, GERENTE})`, lendo `groups` |
| `GET /q/metrics` | 403 | regra por caminho `somente-gerente`, lendo `groups` |
| `GET /agendamentos?cpf=43930413051` (CPF de outro cliente) | 403 | regra de posse do `AgendamentoService`, lendo `upn` |

O `AgendamentoService` continua assim, sem alteração:

```java
  private boolean ehUsuario() {
    return identity.hasRole(Papel.USUARIO);
  }

  private String usuarioLogado() {
    return identity.getPrincipal().getName();
  }
```

`hasRole` responde com o que veio em `groups`; `getPrincipal().getName()` devolve o `upn`. O `AutorizacaoTest` do projeto hands-on (Agenda), com os 14 testes da matriz de permissões, passou inteiro trocando **uma linha**: a do método auxiliar que monta a credencial (seção 8).

401 e 403 continuam significando o mesmo: 401 é "não sei quem você é" (sem token, token inválido, token vencido); 403 é "sei quem você é, e você não pode".

---

## 6. O que a API recusa

O `AutenticacaoTest` da `agenda-atendimento-jwt` fabrica tokens "ruins" e confere que todos dão 401:

| Token | Como o teste fabrica | Resposta |
|---|---|---|
| Com o papel trocado à mão | decodifica as claims, troca `"usuario"` por `"gerente"`, recodifica e mantém a assinatura original | 401 |
| Assinado por outra chave | gera um par RSA novo no teste e assina um token de `gerente` com ele | 401 |
| Vencido | `expiresAt` dez minutos no passado | 401 |
| De outro emissor | `Jwt.issuer("outro-sistema")`, assinado com a chave certa | 401 |
| Que não é um JWT | `Bearer isto.nao.e-um-token` | 401 |

O teste do emissor foi conferido ao contrário: rodando a suíte com o emissor esperado trocado, ele falha (o token de `outro-sistema` passa a entrar). Ou seja, é a linha `mp.jwt.verify.issuer` que barra.

Dois comportamentos que surpreendem:

**Token inválido barra até endpoint público.** O `GET /agencias/0001/horarios` é `@PermitAll` e responde 200 sem credencial nenhuma. Com um token inválido no cabeçalho, responde 401:

```
$ curl -i -H 'Authorization: Bearer abc.def.ghi' localhost:8080/agencias/0001/horarios
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer
```

O Quarkus autentica **antes** de olhar a anotação do endpoint (autenticação proativa, ligada por padrão). Se veio credencial, ela tem de valer. Para o cliente: token vencido atrapalha até as chamadas públicas; renove ou tire o cabeçalho.

**Vencido não é recusado na hora.** Há uma tolerância de relógio de 60 segundos (`mp.jwt.verify.clock.skew`, padrão do guia), para o caso de quem emite e quem confere estarem com relógios um pouco diferentes. Medimos com um token de validade de **5 segundos**:

| Segundos depois da emissão | Resposta |
|---|---|
| 0 | 200 |
| 20 | 200 |
| 60 | 200 |
| 90 | 401 |

Pela configuração, o token de 5 segundos vale 65: medimos que aos 60 ainda entra e aos 90 não.

> **Da 3.15 até a 3.40** — sem mudança registrada nas regras de recusa: `mp.jwt.verify.issuer` e `mp.jwt.verify.clock.skew` (60 segundos) aparecem iguais nos guias da 3.15 e da 3.40.

---

## 7. O preço de não consultar o banco

🎬 **Imagine o cenário.** Um atendente é desligado às 10h00. Às 9h59 ele fez login e recebeu um token. O cadastro dele é apagado às 10h00 em ponto. Até que horas ele ainda consegue consultar a agenda da agência?

O teste `tokenContinuaValendoDepoisQueOUsuarioSaiDoBanco` reproduz isso: cria um usuário, faz login, **apaga o usuário** e tenta de novo.

```java
    // O usuário não existe mais: não consegue um token novo...
    given().contentType(ContentType.JSON).body(Map.of("login", "temporario", "senha", "temp123"))
      .when().post("/login").then().statusCode(401);
    // ...mas o token já emitido segue aceito até expirar: a API confere a assinatura, não o banco.
    given().auth().oauth2(token).when().get("/agencias/0001/agendamentos").then().statusCode(200);
```

Os dois passam. É a contrapartida da seção 4: a API não vai ao banco para conferir o token, então **não fica sabendo** que o usuário saiu, trocou de senha ou perdeu um papel. O que o token diz vale até o `exp` (mais a tolerância da seção 6).

| Decisão | O que pesa |
|---|---|
| **Validade curta** (minutos) | Limita o estrago de um token vazado e o atraso de uma revogação. Custa um login mais frequente |
| ***Refresh token*** | Um segundo token, de vida longa, guardado pelo cliente e usado **só** para pedir um token de acesso novo. Esse pedido vai ao banco, e é ali que a revogação acontece. Não implementamos aqui |
| **Lista de tokens revogados** | A API consulta uma lista (pelo `jti`) a cada chamada. Funciona, e devolve parte do custo que o token tinha tirado |
| **Papéis no token** | Mudança de papel só vale no próximo login. Se isso for inaceitável para uma operação, confira no banco **naquela** operação |
| **Chave privada** | É o segredo do sistema inteiro. Fora do repositório, com acesso restrito, e com plano de troca |

Sobre CSRF, vale o que a seção 6 da Aula 8 disse do Basic: o cabeçalho `Authorization: Bearer` é montado pelo cliente a cada chamada, o navegador não o anexa sozinho. Se o token for guardado em **cookie**, o risco volta.

💭 **Pare e reflita.** A validade de 300 segundos é o padrão da biblioteca, não uma decisão de negócio. Para o aplicativo do cliente e para o sistema do atendente na agência, você escolheria o mesmo número? O que pesa em cada caso?

---

## 8. Testes

Um auxiliar faz o login de verdade e devolve a requisição já com o cabeçalho (`Tokens.java`, em `src/test`; imports: o `given` estático do REST Assured, `io.restassured.http.ContentType`, `io.restassured.specification.RequestSpecification` e `java.util.Map`):

```java
final class Tokens {

  private Tokens() {
  }

  static String de(String login, String senha) {
    return given().contentType(ContentType.JSON).body(Map.of("login", login, "senha", senha))
      .when().post("/login")
      .then().statusCode(200)
      .extract().path("token");
  }

  static RequestSpecification como(String login, String senha) {
    return given().auth().oauth2(de(login, senha));
  }
}
```

No `AutorizacaoTest`, a única mudança:

```java
  private static RequestSpecification como(String usuario, String senha) {
    return Tokens.como(usuario, senha);            // era: given().auth().preemptive().basic(usuario, senha)
  }
```

O que aconteceu com cada grupo de testes:

| Testes | Mudança |
|---|---|
| `AgendamentoResourceTest`, `HorarioResourceTest`, `AgendaDaAgenciaResourceTest`, `CalendarioGatewayTest` (com `@TestSecurity`) | **nenhuma**: a identidade simulada não passa pelo mecanismo |
| `AgendamentoRepositoryTest` e `ExpedienteConfiguravelTest` (não usam HTTP protegido) | **nenhuma** |
| `AutorizacaoTest` (14) e `ObservabilidadeTest` (2) | só a forma de montar a credencial (no `ObservabilidadeTest`: `Tokens.como("atendente", "atendente123")` e `Tokens.como("gerente", "gerente123")`) |
| `AutenticacaoTest` | reescrito: de 8 para 16 testes (login, conteúdo do token, recusas da seção 6, o teste da seção 7) |

Para fabricar os tokens ruins, o teste usa a mesma API do serviço. Dois exemplos:

```java
  @Test
  void tokenExpiradoDevolve401() {
    // Dez minutos atrás: bem além da tolerância de relógio de 60 s (mp.jwt.verify.clock.skew).
    String expirado = Jwt.upn(CLIENTE).groups(Set.of("usuario"))
      .issuedAt(Instant.now().minusSeconds(900))
      .expiresAt(Instant.now().minusSeconds(600))
      .sign();
    given().auth().oauth2(expirado).when().get("/agendamentos").then().statusCode(401);
  }

  @Test
  void tokenAssinadoPorOutraChaveDevolve401() throws Exception {
    KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
    gerador.initialize(2048);
    PrivateKey chaveDeOutro = gerador.generateKeyPair().getPrivate();
    String forjado = Jwt.upn("gerente").groups(Set.of("gerente")).sign(chaveDeOutro);
    given().auth().oauth2(forjado).when().get("/q/metrics").then().statusCode(401);
  }
```

Contagem real da `agenda-atendimento-jwt` (`mvn test`, 13/11/2026): **59 testes, 0 falhas**. Antes de rodar, gere o par de chaves (seção 3.2): a pasta `chaves/` não vai para o repositório.

Se um teste precisar de uma claim específica sem passar pelo login, o guia do Quarkus descreve `@TestSecurity` combinado com `@JwtSecurity(claims = ...)`, da dependência de teste `quarkus-test-security-jwt`. Não usamos aqui.

---

## 9. Com um provedor de identidade: a variação Keycloak

Na `agenda-atendimento-jwt`, a mesma aplicação emite e confere o token. Num ambiente com vários serviços, o desenho costuma ser outro:

```
                       ┌──────────────────────────┐
  login e senha ─────► │ provedor de identidade   │ ──► token (assinado com a chave privada DELE)
                       └──────────────────────────┘
                                                          │
  Authorization: Bearer <token> ──► serviço A (só confere, com a chave pública do provedor)
  Authorization: Bearer <token> ──► serviço B (só confere, com a chave pública do provedor)
```

**Um** sistema emite e **vários** conferem. A pasta `agenda-atendimento-keycloak` faz isso com o **Keycloak**, um provedor de identidade de código aberto que fala OAuth 2.0 e OpenID Connect (OIDC).

> **Esta variação precisa de Docker** para subir o Keycloak, e por isso fica fora do que fazemos na rede corporativa. Os testes dela rodam sem Docker (seção 9.4).

### 9.1. O que sai e o que entra

| | `agenda-atendimento-jwt` | `agenda-atendimento-keycloak` |
|---|---|---|
| Extensão | `quarkus-smallrye-jwt` + `quarkus-smallrye-jwt-build` + `quarkus-security-jpa` | só `quarkus-oidc` |
| Usuários, senhas e papéis | tabela `Usuario`, carregada pelo `CargaUsuarios` | no Keycloak (`keycloak/realm-agenda.json`) |
| Login | `POST /login` na Agenda | no Keycloak |
| Chaves | par gerado por nós, privada guardada pela Agenda | do Keycloak; a Agenda busca a pública sozinha |
| `Usuario`, `UsuarioRepository`, `CargaUsuarios` | existem | **apagadas** |
| `LoginResource`, `TokenService` e companhia | existem | não existem |
| `@RolesAllowed`, regra de posse, regras do `/q/*`, `Papel` | — | **iguais** às da Aula 8 |

A Agenda fica sem senha, sem tabela de usuários e sem chave privada. A configuração inteira são três linhas:

```properties
quarkus.oidc.auth-server-url=http://localhost:8180/realms/agenda
quarkus.oidc.client-id=agenda-atendimento
quarkus.oidc.application-type=service
```

`application-type=service` diz que a Agenda é uma API que recebe Bearer, não uma aplicação web que redireciona para a tela de login.

### 9.2. O Keycloak com os usuários de aula

O `docker-compose.yml` da pasta sobe o Keycloak na porta 8180 e importa o realm `agenda`:

```yaml
name: agenda-keycloak
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.2
    command: start-dev --import-realm
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    ports:
      - "8180:8080"
    volumes:
      - ./keycloak/realm-agenda.json:/opt/keycloak/data/import/realm-agenda.json:ro
```

Um **realm** é um conjunto isolado de usuários, papéis e aplicações. O `keycloak/realm-agenda.json` declara os três papéis (`usuario`, `atendente`, `gerente`), a aplicação `agenda-atendimento` e os mesmos quatro usuários do `CargaUsuarios`, com as mesmas senhas de aula:

| Login | Senha | Papel |
|---|---|---|
| `97744180003` | `senha123` | `usuario` |
| `43930413051` | `senha123` | `usuario` |
| `atendente` | `atendente123` | `atendente` |
| `gerente` | `gerente123` | `gerente` |

Senha em arquivo versionado e `admin`/`admin` só servem para aula, como na Aula 7.

### 9.3. Usando

Suba o Keycloak (`docker compose up -d`) e a Agenda. O token agora é pedido **ao Keycloak**:

```
$ curl -s -X POST localhost:8180/realms/agenda/protocol/openid-connect/token \
  -d grant_type=password -d client_id=agenda-atendimento \
  -d username=97744180003 -d password=senha123
{"access_token":"eyJhbGciOi...","expires_in":300,"refresh_expires_in":1800,"refresh_token":"eyJhbGciOi...","token_type":"Bearer",...}
```

Repare no `refresh_token`: o Keycloak já entrega o que a seção 7 descreveu só em conceito. Com senha errada, quem responde é ele: `{"error":"invalid_grant","error_description":"Invalid user credentials"}`.

As claims do `access_token` (saída real, resumida):

```json
{"exp":1794607145,"iat":1794606845,"iss":"http://localhost:8180/realms/agenda",
 "sub":"c499ad21-11d7-4871-9daa-f14722641dce","azp":"agenda-atendimento",
 "realm_access":{"roles":["usuario"]},
 "preferred_username":"97744180003","name":"Maria Cliente","email":"97744180003@agenda.example"}
```

Não há `upn` nem `groups`. O `quarkus-oidc` tirou o nome de `preferred_username` e os papéis de `realm_access.roles`, sem nenhuma linha de configuração nossa. Com o `access_token` de cada usuário, respostas reais da Agenda:

| Chamada | `usuario` (97744180003) | `atendente` | `gerente` |
|---|---|---|---|
| `GET /agendamentos` | 200 | | |
| `GET /agendamentos?cpf=43930413051` | 403 (posse) | | |
| `GET /agencias/0001/agendamentos` | 403 | 200 | 200 |
| `GET /q/metrics` | 403 | 403 | 200 |
| `POST /agendamentos` | | | 403 |

Sem token, 401 com `www-authenticate: Bearer`; com Basic, 401; token inválido em endpoint público, 401. O mesmo comportamento da variação JWT.

O pedido de token com `grant_type=password` (o cliente entrega a senha do usuário ao provedor) está aqui porque cabe num `curl`. Num sistema real, o usuário digita a senha **na tela do próprio provedor**, e a aplicação cliente nunca a vê (fluxo *authorization code*).

**E se o Keycloak cair?** Paramos o Keycloak e subimos a Agenda:

| Chamada, com o Keycloak fora do ar | Resposta |
|---|---|
| `GET /q/health/live` | 200 |
| `GET /agencias/0001/horarios` (público, sem token) | 200 |
| `GET /agendamentos` sem token | 401 |
| `GET /agencias/0001/agendamentos` com token | **500** |

A Agenda sobe (o *log* avisa `OIDC server is not available yet, an attempt to connect will be made during the first request`), mas não tem a chave pública para conferir token nenhum. Religado o Keycloak, o mesmo processo da Agenda voltou a responder 200, sem reinício.

### 9.4. Testes sem Keycloak

Nos testes, quem faz o papel do provedor é o `OidcWiremockTestResource`, da dependência de teste `quarkus-test-oidc-server`: um servidor simulado que publica uma chave e assina tokens com o login e o papel pedidos. A Agenda confere esses tokens pelo mesmo caminho de produção (`Tokens.java` e o início do `AutenticacaoTest.java`):

```java
  static String de(String login, String papel) {
    return OidcWiremockTestResource.getAccessToken(login, Set.of(papel));
  }
```

```java
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class AutenticacaoTest {
```

```properties
# Nos testes, o provedor é simulado (OidcWiremockTestResource publica o endereço em keycloak.url).
%test.quarkus.oidc.auth-server-url=${keycloak.url}/realms/quarkus/
```

O `AutorizacaoTest` roda os mesmos 14 testes da matriz de permissões. O `AutenticacaoTest` tem 8: os de login e de senha em BCrypt saíram, porque login e senha deixaram de ser assunto da Agenda. Contagem real (`mvn test`, 13/11/2026): **51 testes, 0 falhas**.

> **Da 3.15 até a 3.40** — Na 3.15, o Dev Services de OIDC (que sobe um Keycloak sozinho em dev e teste) exige Docker; na **3.20** ele passa a subir um servidor embutido sem Docker (quadro da seção 1 da Aula 7). Não conferimos outras mudanças do `quarkus-oidc` entre as duas versões.

💭 **Pare e reflita.** Na variação Keycloak, a Agenda responde 500 a quem tem token quando o provedor está fora do ar na subida. Que recurso da Aula 4 avisaria o orquestrador de que a aplicação ainda não está pronta para receber tráfego?

---

### No Spring era assim

| Você fazia no Spring Boot | No Quarkus |
|---|---|
| `spring-boot-starter-oauth2-resource-server` | `quarkus-smallrye-jwt` |
| `http.oauth2ResourceServer(o -> o.jwt(...))` no `SecurityFilterChain` | basta a extensão e as linhas `mp.jwt.verify.*` |
| `JwtDecoder` (`NimbusJwtDecoder.withPublicKey(...)`) | `mp.jwt.verify.publickey.location` |
| `JwtEncoder` (`NimbusJwtEncoder`) e `JwtClaimsSet` | `Jwt.upn(...).groups(...).sign()` (`quarkus-smallrye-jwt-build`) |
| `JwtAuthenticationConverter` para tirar as *authorities* de uma claim | claim `groups` vira papel sem código (outra claim: `smallrye.jwt.path.groups`) |
| `SessionCreationPolicy.STATELESS` | não há sessão para desligar |
| `@AuthenticationPrincipal Jwt jwt` | `@Inject JsonWebToken jwt` |
| `PasswordEncoder.matches(senha, hash)` | `BcryptUtil.matches(senha, hash)` |

---

**Para ir além**

- JWT no Quarkus (conferir o token, propriedades `mp.jwt.verify.*`): https://quarkus.io/version/3.15/guides/security-jwt · versão 3.40: https://quarkus.io/guides/security-jwt
- Montar e assinar tokens (`Jwt`, propriedades `smallrye.jwt.new-token.*`): https://quarkus.io/version/3.15/guides/security-jwt-build
- OIDC com Bearer token (`quarkus-oidc`, `OidcWiremockTestResource`): https://quarkus.io/version/3.15/guides/security-oidc-bearer-token-authentication
- Mecanismos de autenticação e autenticação proativa: https://quarkus.io/version/3.15/guides/security-authentication-mechanisms
- OpenAPI e Swagger UI (`quarkus.smallrye-openapi.security-scheme`): https://quarkus.io/version/3.15/guides/openapi-swaggerui
- RFC 7519, JSON Web Token: https://www.rfc-editor.org/rfc/rfc7519
- RFC 6750, uso de *bearer token* em HTTP: https://www.rfc-editor.org/rfc/rfc6750

**Para o projeto final**

Esta leitura é complementar e **não muda os critérios do projeto final**: o que a rubrica pede continua sendo o da Aula 7 (usuários em banco, senha em BCrypt) e o da Aula 8. Se o grupo quiser trocar o Basic por token, os cuidados são os deste texto: chave privada fora do repositório, validade declarada no README, testes de 401 para token ausente, vencido e adulterado, e os mesmos testes de 403 da Aula 8.
