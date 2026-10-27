# Estado após a Aula 1: primeiro projeto Quarkus

**Foco:** gerar o projeto pelo plugin Maven, entender a estrutura e o modo de desenvolvimento, escrever o primeiro `@QuarkusTest`.

## O que funciona
- `GET /chaves` devolve 3 chaves fixas em JSON (`ChavePixResource`, lista em memória).
- `TipoChave` (enum `CPF`, `EMAIL`, `TELEFONE`, `ALEATORIA`) e `ChavePixResponse` (record).
- Módulo gerado com `quarkus-maven-plugin:3.15.7:create` (comando em `../../inicio/README.md`); só a extensão `quarkus-rest-jackson`.
- Removidos do que o plugin gera: `GreetingResource`, os testes de exemplo, `src/main/docker/`, `.dockerignore` e o Maven Wrapper.

## O que muda em relação ao início
Nasce o módulo `chaves-pix` dentro do agregador (o plugin acrescenta o `<module>` sozinho).

**Ponte do Spring:** `@Path` + `@GET` (Jakarta REST) fazem o papel de `@RestController` + `@GetMapping`. Não há classe
`main` nem `@SpringBootApplication`: o Quarkus monta a aplicação em tempo de build. `@QuarkusTest` equivale a
`@SpringBootTest(webEnvironment = RANDOM_PORT)` com RestAssured já apontado para a aplicação.

## Testes
`mvn -B verify` (17/10/2026): **1 teste, 0 falhas** (`ChavePixResourceTest`).

## Como rodar
```bash
mvn verify
cd chaves-pix && mvn quarkus:dev     # http://localhost:8080/chaves · Dev UI: /q/dev-ui
```

## Próxima aula
CRUD completo em camadas (resource → service → repository), com os códigos HTTP certos e documentação OpenAPI.
