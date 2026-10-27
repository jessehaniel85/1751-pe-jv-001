# Estado após a Aula 5: REST Client e Fault Tolerance

**Foco:** dois serviços conversando por HTTP; o que fazer quando o outro lado demora ou cai.

## O que funciona
- Módulo novo `contas` (porta 8081; pacote `tech.ada.pix.contas`): `Conta` (Active Record do Panache),
  `ContaResource` com `GET /contas/{agencia}/{numero}` → 200 ou 404; 3 contas no `import.sql`.
- No `chaves-pix`:
  - `ContasClient` (`@RegisterRestClient(configKey = "contas")`, URL em `quarkus.rest-client.contas.url`), com
    `@ClientExceptionMapper`: 404 do contas vira `ContaInexistenteException` (422 para quem chamou o chaves-pix).
  - `ContasGateway.confirmarConta`, chamado no POST e no PUT, com `@Timeout(500)`, `@Retry(maxRetries = 2)`,
    `@CircuitBreaker(requestVolumeThreshold = 4)` e `@Fallback` → `ContasIndisponivelException` → **503**. Não cadastra
    chave sem conta confirmada. Conta inexistente não é repetida nem conta como falha no circuito (`abortOn`/`skipOn`).
  - Os mesmos valores em `application.properties` (`tech.ada.pix.chaves.ContasGateway/confirmarConta/Timeout/value=500`,
    ...), com o comentário de que são didáticos. A propriedade vence a anotação.
  - `ContasReadinessCheck`: `/q/health/ready` fica DOWN (503) se o contas não responder.
- `quarkus.http.test-port=0` nos dois módulos: o padrão dos testes é a 8081, a mesma porta do contas em `quarkus:dev`.
- Extensões novas: `quarkus-rest-client-jackson`, `quarkus-smallrye-fault-tolerance`; teste: `quarkus-junit5-mockito`.

## O que muda em relação à Aula 4
Novo módulo `contas`; no `chaves-pix`, `ContaResponse`, `ContasClient`, `ContasGateway`, `ContasReadinessCheck` e duas
exceções. O service confere as regras locais (duplicada, limite) antes da chamada remota.

**Ponte do Spring:** `@RegisterRestClient` é o `@FeignClient` (ou `@HttpExchange`) do Quarkus. As anotações de Fault
Tolerance equivalem às do Resilience4j (`@TimeLimiter`, `@Retry`, `@CircuitBreaker` com `fallbackMethod`), mas são
padrão MicroProfile e configuráveis por propriedade sem código extra.

## Testes
`mvn -B verify` (18/10/2026): **34 testes, 0 falhas**: `chaves-pix` 31 (`ContasGatewayTest` 5, `ContasClientTest` 2,
`ObservabilidadeTest` 5 e os anteriores) + `contas` 3. O `ContasGatewayTest` simula o contas com `@InjectMock @RestClient`
e prova: conta inexistente → 422 com 1 chamada; falha → 3 tentativas e 503 sem gravar; resposta de 800 ms → timeout e
503; circuito aberto → nenhuma chamada ao contas. Cada teste começa com `CircuitBreakerMaintenance.resetAll()`.
Contraprova: sem `@Timeout` e `@Fallback`, 3 dos 5 testes falham.

Verificado também de ponta a ponta com os dois `quarkus-run.jar` no ar (17/10/2026, quando o `import.sql` ainda carregava em prod): 201 (conta existente), 422 (inexistente),
503 e readiness 503 com o contas derrubado.
Desde 18/10/2026 os dados de exemplo só carregam em dev e test: no jar, o `contas` sobe sem nenhuma conta (todo `POST`
de chave dá 422). Para a demonstração de ponta a ponta, use `mvn quarkus:dev` nos dois módulos.

## Como rodar
```bash
mvn verify
cd contas && mvn quarkus:dev -Ddebug=false     # 8081
cd chaves-pix && mvn quarkus:dev               # 8080, outro terminal
curl -i -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"ALEATORIA","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
# derrube o contas (q) e repita: 503
```

## Próxima aula
Hoje um CPF com dígito errado é aceito e os erros saem sem corpo. Na Aula 6: validação por tipo de chave e corpo de erro uniforme.
