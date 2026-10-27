# Material do Aluno — Aula 7: Autenticação (quem está chamando?)

> **Sexta, 13/11/2026 · Tempo de leitura:** ~13 min. Até agora, qualquer pessoa cadastra, troca e apaga chaves em nome de qualquer CPF. Hoje a API passa a exigir login. Faremos em dois passos, os dois ao vivo: primeiro com usuários escritos no `application.properties` (o jeito mais rápido de ver o mecanismo funcionando) e depois com usuários no banco e senha guardada como hash BCrypt (o estado final do gabarito). Autorização por papel fica para a Aula 8.

---

## 1. Como o Quarkus descobre quem está chamando

🎬 **Imagine o cenário.** Uma rede de correspondentes bancários (lotéricas, mercados) usa um sistema para pagamento de contas e saque. Cada loja tem vários operadores, que entram e saem com frequência. Hoje, todos os operadores de uma loja usam o mesmo login, e a senha está num papel colado no balcão. A auditoria quer saber quem fez cada operação, e ninguém consegue responder.

O Quarkus Security separa o problema em três peças:

```
requisição ──► mecanismo de        ──► provedor de          ──► SecurityIdentity
               autenticação            identidade               (nome e papéis)
               (Basic: lê o            (properties, JPA:
               cabeçalho               confere usuário
               Authorization)          e senha)
```

- **Mecanismo de autenticação:** como a credencial chega. Hoje usamos **HTTP Basic**: o cliente manda `Authorization: Basic <base64 de usuario:senha>`. Liga com `quarkus.http.auth.basic=true`. Existem outros (formulário, mTLS e, com extensões, JWT e OIDC).
- **Provedor de identidade:** onde o usuário e a senha são conferidos. Hoje, primeiro em properties (`quarkus-elytron-security-properties-file`) e depois no banco (`quarkus-security-jpa`).
- **`SecurityIdentity`:** o resultado, com o nome do usuário e os papéis. Pode ser injetado (`@Inject SecurityIdentity`), e é o que a Aula 8 usa para a regra "só as próprias chaves".

E dois códigos que você vai ver muito:

| Código | Significa | Exemplo |
|---|---|---|
| `401 Unauthorized` | não sei quem você é: faltou credencial ou ela está errada | `curl` sem `-u`, ou senha errada |
| `403 Forbidden` | sei quem você é, mas você não pode fazer isso | auditor tentando apagar uma chave (Aula 8) |

💭 **Pare e reflita.** Guardar a senha criptografada é suficiente? Qual é a diferença entre **criptografar** (dá para desfazer com a chave) e **fazer hash** (não dá para desfazer)? Qual dos dois serve para senha?

> **Da 3.15 até a 3.40** — A arquitetura (mecanismo, provedor de identidade, `SecurityIdentity`) não muda. Na **3.20**, o Dev Services de OIDC passa a subir um servidor de identidade embutido **sem Docker**, o que abre caminho para testar OIDC em máquinas sem contêiner; na 3.15, ele exige Docker.

---

## 2. Passo 1: usuários no `application.properties`

Acrescente a extensão `quarkus-elytron-security-properties-file` (o provedor de properties é o mesmo **Elytron** do WildFly/JBoss) e estas linhas, que estão no `ESTADO.md` do gabarito da Aula 7 e foram conferidas em execução:

```properties
quarkus.http.auth.basic=true
# Só em dev e test, e só em aula: senha em texto puro no arquivo.
%dev,test.quarkus.security.users.embedded.enabled=true
%dev,test.quarkus.security.users.embedded.plain-text=true
%dev,test.quarkus.security.users.embedded.users."52998224725"=titular123
%dev,test.quarkus.security.users.embedded.roles."52998224725"=titular
```

Três detalhes:
- O username do titular é o CPF (vai ser útil na Aula 8). Como é um número, a chave vai **entre aspas**.
- `%dev,test.` aplica a linha nos perfis de dev e de teste ao mesmo tempo. Em produção, nada disso existe.
- Para um segundo usuário, repita o par `users.<nome>` e `roles.<nome>` (nós acrescentamos `atendente` com a senha `atendente123` para as saídas abaixo).

Agora a surpresa para quem vem do Spring. Com a extensão no projeto e **nenhuma anotação** no recurso, rode `curl -i localhost:8080/chaves`. Saída real:

```
HTTP/1.1 200 OK
content-length: 455
Content-Type: application/json;charset=UTF-8
```

**No Quarkus, endpoint sem anotação de segurança (e sem regra por caminho) continua aberto.** Ligar a segurança não fecha nada sozinho. Para exigir login, anote o recurso com `@Authenticated` (`io.quarkus.security.Authenticated`):

```java
@Authenticated
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChavePixResource {
```

✍️ **Experimente agora.** Com o `quarkus:dev` no ar, repita as chamadas. Saídas reais:

```
$ curl -i localhost:8080/chaves
HTTP/1.1 401 Unauthorized
www-authenticate: basic
content-length: 0

$ curl -i -u atendente:errada localhost:8080/chaves
HTTP/1.1 401 Unauthorized
www-authenticate: basic
content-length: 0

$ curl -i -u atendente:atendente123 localhost:8080/chaves
HTTP/1.1 200 OK
```

O cabeçalho `www-authenticate: basic` é o servidor dizendo "eu aceito Basic; mande a credencial". Agora veja o que o `curl -u` envia (`-v`) e decodifique:

```
$ curl -s -v -u atendente:atendente123 localhost:8080/chaves -o /dev/null 2>&1 | grep -i authorization
> Authorization: Basic YXRlbmRlbnRlOmF0ZW5kZW50ZTEyMw==

$ echo -n 'YXRlbmRlbnRlOmF0ZW5kZW50ZTEyMw==' | base64 -d
atendente:atendente123
```

**Base64 é codificação, não proteção.** Qualquer um que veja o tráfego lê a senha. Basic só faz sentido com TLS (HTTPS).

Sobre a senha no arquivo: sem `plain-text=true`, o realm em memória espera um hash do tipo *digest*, e o algoritmo é escolhido em `quarkus.security.users.embedded.algorithm`, que só aceita valores `digest-*`. **BCrypt não é opção nesse realm**; ele aparece no passo 2. Tutoriais (inclusive o texto original deste módulo) citam uma propriedade `...password-hashing.algorithm=bcrypt`: ela não existe. O próprio guia do Quarkus diz que usuários em properties são para teste.

> **Da 3.15 até a 3.40** — Na **3.30**, o algoritmo padrão de `quarkus.security.users.embedded.algorithm` muda de MD5 para SHA-512. `quarkus.http.auth.basic` e `@Authenticated`: sem mudança relevante até a 3.40.

---

## 3. Passo 2: usuários no banco, senha em BCrypt

Troque a extensão de properties por `quarkus-security-jpa` e **apague** as linhas `quarkus.security.users.embedded.*` (o gabarito final não as tem; mantenha `quarkus.http.auth.basic=true`). O usuário vira uma entidade (`gabarito/aula-07/chaves-pix/src/main/java/tech/ada/pix/chaves/Usuario.java`):

```java
@Entity
@UserDefinition
public class Usuario extends PanacheEntity {

    @Username
    @Column(nullable = false, unique = true)
    public String username;

    @Password
    @Column(nullable = false)
    public String senha;

    /** Papéis separados por vírgula (ex.: "titular"). */
    @Roles
    @Column(nullable = false)
    public String papeis;

    public static void adicionar(String username, String senha, String papeis) {
        Usuario usuario = new Usuario();
        usuario.username = username;
        usuario.senha = BcryptUtil.bcryptHash(senha);
        usuario.papeis = papeis;
        usuario.persist();
    }
}
```

`@UserDefinition` diz ao Quarkus onde estão login, senha e papéis; ele mesmo confere a senha no login. Você não escreve a consulta nem a comparação. `@Password` espera o hash no formato do BCrypt, gerado por `BcryptUtil.bcryptHash(...)`.

Os usuários de aula são criados quando a aplicação sobe (`CargaDeUsuarios.java`):

```java
@ApplicationScoped
public class CargaDeUsuarios {

    @Transactional
    void carregar(@Observes StartupEvent evento) {
        Usuario.deleteAll();
        Usuario.adicionar("52998224725", "titular123", "titular");
        Usuario.adicionar("11144477735", "titular123", "titular");
        Usuario.adicionar("12345678909", "titular123", "titular");
        Usuario.adicionar("atendente", "atendente123", "atendente");
        Usuario.adicionar("auditor", "auditor123", "auditor");
    }
}
```

- **Por que não no `import.sql`?** Porque a senha precisa passar pelo BCrypt antes de ir para o banco. No SQL ela iria em texto puro, ou com um hash feito à mão.
- **Por que `@Transactional`?** Sem transação, a subida falha com `TransactionRequiredException` já no `deleteAll()` (e o `persist()` falharia do mesmo jeito), o mesmo erro da Aula 3.
- **Senhas conhecidas de todos** só servem para aula (o comentário do gabarito diz isso). Em produção, usuário vem de cadastro ou de um provedor de identidade.

Por que BCrypt e não SHA-256? Porque SHA é rápido, e rápido é ruim para senha: facilita a força bruta. BCrypt é lento de propósito e gera um **sal** diferente por senha. Duas pessoas com a mesma senha têm hashes diferentes. No banco, a coluna `senha` guarda algo que começa com `$2a$`; o teste `senhaFicaGuardadaComoHashBcrypt` do gabarito prova isso.

O recurso continua com `@Authenticated`. Com o gabarito da Aula 7 no ar:

```bash
curl -i localhost:8080/chaves                              # 401
curl -u 52998224725:titular123 localhost:8080/chaves       # 200
```

💭 **Pare e reflita.** No sistema legado que você conhece, como era a autenticação? Login próprio com tabela de usuários, LDAP, um *filter* escrito à mão? A senha estava guardada como?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 registrada na revisão: `quarkus-security-jpa`, `@UserDefinition` e `BcryptUtil` continuam iguais.

---

## 4. Testar quem pode entrar

Dois jeitos, e o gabarito usa os dois.

**Com credencial real**, passando pelo caminho de verdade (`gabarito/aula-07/chaves-pix/src/test/java/tech/ada/pix/chaves/AutenticacaoTest.java`, trecho):

```java
@Test
void semCredencialDevolve401ComDesafioBasic() {
    given().when().get("/chaves")
            .then().statusCode(401)
            .header("WWW-Authenticate", containsStringIgnoringCase("basic"));
}

@Test
void senhaErradaDevolve401() {
    given().auth().preemptive().basic("52998224725", "errada")
            .when().get("/chaves")
            .then().statusCode(401);
}

@Test
void credencialCorretaDevolve200() {
    given().auth().preemptive().basic("52998224725", "titular123")
            .when().get("/chaves")
            .then().statusCode(200);
}
```

O `preemptive()` manda o cabeçalho já na primeira requisição, sem esperar o `401` do desafio.

**Com identidade simulada**, para os testes que não são de segurança. Assim que o recurso ganha `@Authenticated`, todos os testes antigos passam a receber `401`. O gabarito resolve com `@TestSecurity` na classe (`ChavePixResourceTest.java`):

```java
@QuarkusTest
@TestSecurity(user = "52998224725", roles = "titular")
class ChavePixResourceTest {
```

`@TestSecurity` exige a dependência de teste `quarkus-test-security`. Sem ela, o import `io.quarkus.test.security` não compila:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-security</artifactId>
    <scope>test</scope>
</dependency>
```

Contagem real do gabarito da Aula 7 (`mvn verify`, 18/10/2026): **63 testes, 0 falhas** (60 no `chaves-pix`, sendo 6 do `AutenticacaoTest`, + 3 no `contas`).

> **Da 3.15 até a 3.40** — Na **3.31**, `quarkus-junit5` passa a se chamar `quarkus-junit` (com redirecionamento do nome antigo). `@TestSecurity` e `quarkus-test-security`: sem mudança relevante até a 3.40.

---

## 5. Aberto por padrão: escolha consciente

Resumindo a diferença que mais pega quem vem do Spring:

| | Spring Security | Quarkus Security |
|---|---|---|
| Ao acrescentar a dependência | tudo fica fechado (e aparece até uma tela de login) | nada muda: endpoint sem anotação continua aberto |
| Para fechar | configurar o que abre | anotar (`@Authenticated`, `@RolesAllowed`) ou criar regra por caminho |
| Para mudar o padrão | — | `quarkus.security.jaxrs.deny-unannotated-endpoints=true` |

Repare também que o `/q/health/live` continua respondendo sem login: o `@Authenticated` está no `ChavePixResource`, não nos endpoints do Quarkus. O teste `healthContinuaAbertoParaOOrquestrador` prova isso. A Aula 8 deixa essa decisão explícita por configuração.

✍️ **Experimente agora.** Num branch à parte, ligue `quarkus.security.jaxrs.deny-unannotated-endpoints=true` e crie um endpoint novo sem anotação nenhuma. O que ele responde? E o `/q/health/live`? Vale ligar no seu projeto final?

💭 **Pare e reflita.** Um agente de IA que chama a sua API também é um usuário. Que credencial ele deveria ter, com quais papéis, e onde essa credencial deveria ficar (certamente não no prompt)?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 registrada na revisão: endpoint sem anotação continua aberto por padrão.

---

### No Spring era assim

| Você fazia no Spring Boot | No Quarkus |
|---|---|
| `spring-boot-starter-security` (fecha tudo) | extensão de identidade (`quarkus-elytron-security-properties-file` ou `quarkus-security-jpa`); nada fecha sozinho |
| `http.httpBasic()` no `SecurityFilterChain` | `quarkus.http.auth.basic=true` |
| `.anyRequest().authenticated()` | `@Authenticated` no recurso |
| `InMemoryUserDetailsManager` | `quarkus.security.users.embedded.*` (só dev e teste) |
| `UserDetailsService` + repositório JPA | entidade com `@UserDefinition`, `@Username`, `@Password`, `@Roles` |
| `BCryptPasswordEncoder` | `BcryptUtil.bcryptHash(...)` |
| `SecurityContextHolder.getContext().getAuthentication()` | `@Inject SecurityIdentity` |
| `@WithMockUser(roles = "X")` | `@TestSecurity(user = "u", roles = "X")` + `quarkus-test-security` |

---

**Para ir além** (guias oficiais da 3.15)

- Arquitetura do Quarkus Security: https://quarkus.io/version/3.15/guides/security-architecture
- Basic auth: https://quarkus.io/version/3.15/guides/security-basic-authentication-howto
- Usuários em properties: https://quarkus.io/version/3.15/guides/security-properties
- Quarkus Security com Jakarta Persistence: https://quarkus.io/version/3.15/guides/security-jpa
- Tutorial Basic + JPA: https://quarkus.io/version/3.15/guides/security-getting-started-tutorial
- Testes de segurança: https://quarkus.io/version/3.15/guides/security-testing

**Para levar ao projeto final** (critério 5 da rubrica)

- Usuários em banco, com `quarkus-security-jpa` e senha em BCrypt: é **CORE** do projeto final. Usuários em properties servem só de degrau; a entrega é no banco.
- Testes de `401` (sem credencial e com senha errada) e de sucesso, com credencial real, não só `@TestSecurity`.
- Definir agora os **pelo menos 2 papéis** que a Aula 8 vai usar, e registrar os usuários de teste (fictícios) no README.

> **Na próxima aula:** agora a API sabe quem está chamando. O atendente pode apagar a chave de qualquer cliente? E um titular pode ver as chaves de outro titular?
