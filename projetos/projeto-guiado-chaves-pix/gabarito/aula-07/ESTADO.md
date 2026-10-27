# Estado após a Aula 7: autenticação

**Foco:** Quarkus Security com HTTP Basic; usuários em arquivo de propriedades e, depois, no banco com hash de senha.

## O que funciona (passo final)
- `quarkus.http.auth.basic=true` e `@Authenticated` no `ChavePixResource`: sem credencial ou com senha errada, **401**
  com `WWW-Authenticate: basic`.
- `Usuario` (`@UserDefinition`, `quarkus-security-jpa`): `@Username`, `@Password` (hash BCrypt) e `@Roles`.
- `CargaDeUsuarios`: `@Observes StartupEvent` + `@Transactional` grava 5 usuários com `BcryptUtil.bcryptHash`
  (tabela de usuários em `../README.md`). Não dá para usar `import.sql`: a senha precisa passar pelo BCrypt antes.
- `/q/health/*` continua aberto (a Aula 8 formaliza isso por configuração).
- Extensão nova: `quarkus-security-jpa`; teste: `quarkus-test-security` (para `@TestSecurity`).

## Passo 1 da aula (antes do banco): usuários em properties
Com a extensão `quarkus-elytron-security-properties-file` no lugar da `quarkus-security-jpa`:

```properties
quarkus.http.auth.basic=true
# Só em dev e test, e só em aula: senha em texto puro no arquivo.
%dev,test.quarkus.security.users.embedded.enabled=true
%dev,test.quarkus.security.users.embedded.plain-text=true
%dev,test.quarkus.security.users.embedded.users."52998224725"=titular123
%dev,test.quarkus.security.users.embedded.roles."52998224725"=titular
```

O username é número, por isso as aspas na chave. Sem `plain-text=true`, o valor esperado é um hash *digest*
(`quarkus.security.users.embedded.algorithm`, só `digest-*`; bcrypt não existe neste realm). Verificado em 17/10/2026
numa cópia descartável deste gabarito: `AutenticacaoTest` com 5 testes, 0 falhas.

## O que muda em relação à Aula 6
Entram `Usuario`, `CargaDeUsuarios` e `@Authenticated`. Os testes antigos ganham `@TestSecurity(user = ..., roles = "titular")`
na classe; o `AutenticacaoTest` usa credenciais reais, sem `@TestSecurity`.

**Ponte do Spring:** `quarkus.http.auth.basic=true` + `@Authenticated` equivalem a `httpBasic()` + `authenticated()`
no `SecurityFilterChain`; `@UserDefinition` substitui o `UserDetailsService` que você escreveria; `@TestSecurity` é o `@WithMockUser`.

## Testes
`mvn -B verify` (18/10/2026): **63 testes, 0 falhas**: `chaves-pix` 60 (`AutenticacaoTest` 6 e os anteriores) + `contas` 3.

## Como rodar
```bash
mvn verify
curl -i localhost:8080/chaves                              # 401
curl -u 52998224725:titular123 localhost:8080/chaves       # 200
```

## Próxima aula
Todo usuário logado pode tudo. Na Aula 8: o atendente pode ver a chave de qualquer cliente? (autorização)
