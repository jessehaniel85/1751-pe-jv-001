# Gabarito evolutivo: API de Chaves PIX

Estado da aplicação **ao fim de cada aula**, em pastas independentes. Cada `aula-0X/` é um projeto Maven completo
(pom agregador + módulos) que passa no `mvn verify`. Use para estudar antes da aula, para comparar com o seu
projeto e para retomar se o live coding travar.

| Aula | Pasta | Incremento | Testes |
|---|---|---|---|
| 1 | `aula-01/` | projeto gerado pelo plugin; `GET /chaves` com lista fixa | 1 |
| 2 | `aula-02/` | CRUD em memória em camadas; 201/204/404/409/422; OpenAPI e Swagger UI | 13 |
| 3 | `aula-03/` | Panache (Repository), H2, `@Transactional`, `import.sql` | 18 |
| 4 | `aula-04/` | `@ConfigMapping`, perfis, health, métricas Micrometer | 22 |
| 5 | `aula-05/` | módulo `contas`; REST Client; Fault Tolerance com 503 | 31 + 3 |
| 6 | `aula-06/` | `@ChaveValida`; `ErroResposta`; `@ServerExceptionMapper` | 54 + 3 |
| 7 | `aula-07/` | HTTP Basic; usuários em banco com BCrypt | 60 + 3 |
| 8 | `aula-08/` | papéis titular/atendente/auditor; posse da chave; `/q/*` por configuração; CORS | 76 + 3 |

"31 + 3" = testes do `chaves-pix` + testes do `contas`. Contagem real de `mvn -B verify` com JDK 21 e Quarkus 3.15.7,
em 18/10/2026 (somando os relatórios do surefire).

## Como usar

```bash
cd aula-05
mvn verify                                  # todos os módulos
cd contas && mvn quarkus:dev -Ddebug=false  # serviço de contas, porta 8081
cd chaves-pix && mvn quarkus:dev            # em outro terminal: chaves, porta 8080
```

Na rede corporativa, acrescente `-s <settings.xml do Nexus>` (ver checklist de ambiente).

## Usuários de aula (Aulas 7 e 8)

| Usuário | Senha | Papel |
|---|---|---|
| `52998224725` | `titular123` | titular |
| `11144477735` | `titular123` | titular |
| `12345678909` | `titular123` | titular |
| `atendente` | `atendente123` | atendente |
| `auditor` | `auditor123` | auditor |

CPFs fictícios com dígito verificador válido. Senhas de aula: em produção, usuário vem de cadastro ou de um provedor
de identidade.

```bash
curl -u 52998224725:titular123 localhost:8080/chaves
```

## Dados de exemplo

| Módulo | Dados (`import.sql`) |
|---|---|
| `chaves-pix` | 3 chaves: CPF e e-mail do titular 52998224725 (conta 0001/123456), telefone do 11144477735 (0002/654321) |
| `contas` | contas 0001/123456, 0002/654321 e 0001/777777 (titular 12345678909) |

Os ids gerados pulam de 50 em 50 (1, 51, 101): é o `allocationSize` padrão da sequence do Hibernate. Ver `aula-03/ESTADO.md`.
