# Estado após a Aula 2: Quarkus REST + CDI

**Foco:** recursos e respostas HTTP, camadas, escopos e injeção de dependência; documentação OpenAPI.

## O que funciona
- CRUD em memória: `POST /chaves` (201 + `Location`), `GET /chaves` (filtro `?titular=`), `GET /chaves/{id}`,
  `PUT /chaves/{id}` (troca a conta; a própria chave não conta no limite quando a conta não muda), `DELETE /chaves/{id}` (204).
- Regras: duplicada → 409; mais de 5 chaves na conta → 422 (constante `LIMITE_POR_CONTA` em `ChavePixService`,
  "vira configuração na Aula 4"); inexistente → 404. Chave aleatória: o servidor gera o UUID e ignora o valor enviado.
- Camadas: `ChavePixResource` → `ChavePixService` → `ChavePixRepository` (`@ApplicationScoped` com `ConcurrentHashMap`).
  Os métodos do repositório já têm os nomes do Panache (`persist`, `findByIdOptional`, `listAll`, `deleteById`).
- OpenAPI em `/q/openapi`; Swagger UI em `/q/swagger-ui` (dev e test). Extensão nova: `quarkus-smallrye-openapi`
  (`mvn quarkus:add-extension -Dextensions=quarkus-smallrye-openapi`).
- As exceções herdam de `ClientErrorException`/`NotFoundException` e já carregam o status; o corpo do erro sai vazio.

## O que muda em relação à Aula 1
Sai a lista fixa; entram `ChavePix`, `NovaChaveRequest`, `TrocaContaRequest`, service, repository e três exceções.

**Ponte do Spring:** `@ApplicationScoped` + `@Inject` no lugar de `@Service` + `@Autowired` (o bean é criado na 1ª
chamada, por um proxy). `RestResponse<T>` faz o papel de `ResponseEntity<T>`; exceção que estende
`WebApplicationException` equivale a uma exceção com `@ResponseStatus`.

Ponto de atenção ao vivo: `RestResponse.ResponseBuilder.<ChavePixResponse>created(...)` precisa do tipo explícito
entre `< >`; sem ele, o compilador infere `Object` e não compila.

## Testes
`mvn -B verify` (18/10/2026): **13 testes, 0 falhas** (`ChavePixResourceTest`: um por regra e por endpoint, mais o OpenAPI;
o 13º, `putParaAMesmaContaCheiaDevolve200`, prova que a própria chave não conta no limite ao trocar para a mesma conta).

## Como rodar
```bash
mvn verify
cd chaves-pix && mvn quarkus:dev     # Swagger UI: http://localhost:8080/q/swagger-ui
curl -i -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"EMAIL","valor":"maria@exemplo.com.br","agencia":"0001","numeroConta":"123456","titularCpf":"52998224725"}'
```

## Próxima aula
Os dados sobrevivem só enquanto o processo vive. Na Aula 3 vão para o banco (H2) com Hibernate ORM e Panache.
