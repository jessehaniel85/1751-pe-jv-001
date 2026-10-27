# Estado após a Aula 4: MicroProfile Config, Health e métricas

**Foco:** configuração tipada e por perfil; liveness × readiness; métricas Micrometer no formato Prometheus.

## O que funciona
- `PixConfig` (`@ConfigMapping(prefix = "pix")`): `pix.chaves.limite-por-conta=5` no `application.properties`.
  Sobrescreve sem recompilar: `PIX_CHAVES_LIMITE_POR_CONTA=3` ou `-Dpix.chaves.limite-por-conta=3`. Sem a propriedade,
  a aplicação não sobe (erro no boot, não na 1ª requisição).
- Perfis: `%dev` (SQL no log) e `%test` (banco `chaves-test`).
- Health: `/q/health/live` com `ChavesLivenessCheck` (expõe o limite configurado); `/q/health/ready` com o check do
  datasource, que o Agroal registra sozinho.
- Métricas em `/q/metrics`: contador `pix.chaves.cadastradas` com a tag `tipo`
  (`pix_chaves_cadastradas_total{tipo="EMAIL"}`) e `@Timed` no POST (`pix_chaves_cadastro_seconds_*`).
- Extensões novas: `quarkus-smallrye-health`, `quarkus-micrometer-registry-prometheus`.

## O que muda em relação à Aula 3
A constante `LIMITE_POR_CONTA` sai do service; entram `PixConfig`, `ChavesLivenessCheck` e o `MeterRegistry` no service.

**Ponte do Spring:** `@ConfigMapping` equivale a `@ConfigurationProperties`; `%dev.` no início da chave substitui o
`application-dev.properties`. `/q/health/live` e `/q/health/ready` correspondem aos grupos `liveness` e `readiness` do
Actuator, e `/q/metrics` ao `/actuator/prometheus` (Micrometer nos dois).

## Testes
`mvn -B verify` (18/10/2026): **22 testes, 0 falhas** (`ChavePixResourceTest` 13, `ChavePixRepositoryTest` 5,
`ObservabilidadeTest` 3, `LimiteConfiguravelTest` 1). O `LimiteConfiguravelTest` usa `@TestProfile` com limite 1
(só no teste) para provar que o valor vem da configuração.

## Como rodar
```bash
mvn verify
cd chaves-pix && mvn quarkus:dev
curl localhost:8080/q/health/ready
curl -s localhost:8080/q/metrics | grep pix_chaves
mvn quarkus:dev -Dpix.chaves.limite-por-conta=2   # a 3ª chave da mesma conta dá 422
```

## Próxima aula
A conta precisa existir de verdade: nasce o serviço de contas e o chaves-pix passa a consultá-lo, com tolerância a falhas.
