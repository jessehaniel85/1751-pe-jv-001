# Estado após a Aula 8: autorização

**Foco:** RBAC com JSR-250, regra de posse no service, permissões por configuração, CORS e CSRF.

## O que funciona
| Operação | titular (username = CPF) | atendente | auditor |
|---|---|---|---|
| `POST /chaves` | só para o próprio CPF | 403 | 403 |
| `GET /chaves` | só as próprias; `?titular=` com outro CPF → 403 | todas | todas |
| `GET /chaves/{id}` | só as próprias | qualquer | qualquer |
| `PUT /chaves/{id}` | só as próprias | 403 | 403 |
| `DELETE /chaves/{id}` | só as próprias | qualquer (a pedido) | 403 |
| `GET /chaves/tipos` | público (`@PermitAll`) | público | público |

- `@DenyAll` na classe (nega por padrão) e `@RolesAllowed` por método, com as constantes de `Papeis`.
- Posse: `ChavePixService` lê o usuário de `SecurityIdentity` e lança `io.quarkus.security.ForbiddenException`.
  O 403 sai com `ErroResposta`, tanto da posse quanto do `@RolesAllowed` (mapper novo em `ExceptionMappers`).
- `quarkus.http.auth.permission.*` para os caminhos que não são recurso JAX-RS: `/q/health` aberto, `/q/metrics` só
  auditor (política `somente-auditor`), `/q/openapi` e `/q/swagger-ui` para quem tem login.
- CORS: `quarkus.http.cors=true` com origem `http://localhost:3000`. Na 3.15 a chave é essa; **da 3.28 em diante foi
  removida** e o nome é `quarkus.http.cors.enabled=true` (a chave antiga é ignorada e o CORS fica desligado sem aviso).
- CSRF: fica no material, sem extensão no projeto. A API é JSON com HTTP Basic, sem sessão nem formulário;
  `quarkus-rest-csrf` protege formulários e, sem configuração própria para API, faz todo POST JSON responder 400.

## O que muda em relação à Aula 7
`@Authenticated` dá lugar a `@DenyAll` + `@RolesAllowed`; entram `Papeis`, o endpoint `/chaves/tipos`, a regra de posse
no service (o `DELETE` agora busca a chave antes de remover, para checar o dono) e as permissões de `/q/*`. Testes antigos
passam a usar, em `@TestSecurity`, o CPF do titular dono das chaves que criam.

**Ponte do Spring:** `@RolesAllowed` é o mesmo do Spring com `jsr250Enabled`; a posse via `SecurityIdentity` faz o papel
de um `@PreAuthorize("#cpf == authentication.name")`. `quarkus.http.auth.permission.*` equivale ao
`authorizeHttpRequests().requestMatchers(...)`.

## Testes
`mvn -B verify` (18/10/2026): **79 testes, 0 falhas**: `chaves-pix` 76 (`AutorizacaoTest` 16, com credenciais reais,
e os anteriores) + `contas` 3. `mvn quarkus:dev` verificado com os dois serviços no ar: 401 sem login, lista do titular,
chave aleatória criada (201), conta inexistente (422) e atendente tentando cadastrar (403).

## Como rodar
```bash
mvn verify
cd contas && mvn quarkus:dev -Ddebug=false
cd chaves-pix && mvn quarkus:dev
curl -u atendente:atendente123 'localhost:8080/chaves?titular=11144477735'   # 200
curl -i -u auditor:auditor123 -X DELETE localhost:8080/chaves/1              # 403
curl -u auditor:auditor123 localhost:8080/q/metrics | grep pix_chaves        # 200
```

## Próxima aula
Aula 9: bancas do projeto final. O projeto guiado fica como referência para comparar a arquitetura dos grupos.
