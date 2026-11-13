# agenda-atendimento-keycloak

Variação da Agenda de Atendimento (estado do fim da Aula 8) com **Bearer token emitido por um Keycloak**: usuários,
senhas e papéis moram no provedor de identidade, e a Agenda só confere o token (`quarkus-oidc`). Não há tabela de
usuários nem endpoint de login. Explicação na seção 9 de `plano-de-aulas/complemento-aula-07-08-jwt.md`.

Pilha: Quarkus 3.15.7, Java 21. **Para rodar a aplicação é preciso Docker** (o Keycloak); os testes rodam sem Docker.

## Rodar

```bash
mvn test                 # 51 testes, com provedor de identidade simulado
docker compose up -d     # Keycloak em http://localhost:8180 (console: admin / admin, só para a aula)
mvn quarkus:dev
```

O Keycloak importa o realm `agenda` de `keycloak/realm-agenda.json` a cada subida, com os mesmos usuários de aula do
`CargaUsuarios`: `97744180003` e `43930413051` (senha `senha123`, papel `usuario`), `atendente` (`atendente123`) e
`gerente` (`gerente123`).

```bash
curl -s -X POST localhost:8180/realms/agenda/protocol/openid-connect/token \
  -d grant_type=password -d client_id=agenda-atendimento -d username=atendente -d password=atendente123
TOKEN="cole-aqui-o-valor-do-campo-access_token"
curl -H "Authorization: Bearer $TOKEN" localhost:8080/agencias/0001/agendamentos
```

Para derrubar o Keycloak: `docker compose down`.
