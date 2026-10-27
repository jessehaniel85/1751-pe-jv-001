# Estado após a Aula 3: Hibernate ORM com Panache

**Foco:** datasource, perfis, Active Record × Repository, consultas, `@Transactional`.

## O que funciona
- `ChavePix` é entidade (`extends PanacheEntity`, campos públicos, `@UniqueConstraint` em tipo + valor).
- `ChavePixRepository implements PanacheRepository<ChavePix>`, com `findByTitular`, `countByConta` e `existeChave`.
- `@Transactional` em `cadastrar`, `trocarConta` e `remover`. No `PUT` não há `save()`: a entidade está gerenciada e
  o Hibernate grava a mudança no commit (*dirty checking*).
- H2 em memória com datasource explícito e `quarkus.devservices.enabled=false` (sem Docker); `import.sql` com 3 chaves, carregado só em dev e test
  (padrão de `quarkus.hibernate-orm.sql-load-script`, que o gabarito não declara; no jar, perfil prod, a lista começa vazia).
- Perfil `%dev`: SQL no log (`%dev.quarkus.hibernate-orm.log.sql=true`).
- Extensões novas: `quarkus-hibernate-orm-panache`, `quarkus-jdbc-h2`.

## O que muda em relação à Aula 2
`ChavePix` perde getters/setters (o Quarkus os gera no build e reescreve todo acesso ao campo); o repositório em
memória vira `PanacheRepository` e o service quase não muda, porque os nomes dos métodos já eram os do Panache.

**Ponte do Spring:** `PanacheRepository<T>` faz o papel de `JpaRepository<T, Long>`, mas as consultas são escritas
(`list("titularCpf", cpf)`) em vez de derivadas do nome do método. `@Transactional` é o do Jakarta
(`jakarta.transaction`), não o do Spring. O Active Record (consulta na própria entidade) não tem equivalente direto
no Spring Data; aparece no módulo `contas` da Aula 5.

## Perguntas prováveis
- **Por que os ids são 1, 51, 101?** A sequence que o Hibernate cria tem incremento 50 (`allocationSize` padrão):
  ele reserva 50 ids por ida ao banco. O `import.sql` usa `next value for ChavePix_SEQ`, então não colide com o
  primeiro `persist()`; id fixo (`values (1, ...)`) colidiria.
- **Sem `@Transactional` no POST?** O `persist()` falha com erro 500 (não há transação ativa).

## Testes
`mvn -B verify` (18/10/2026): **18 testes, 0 falhas** (`ChavePixResourceTest` 13, `ChavePixRepositoryTest` 5).
`mvn quarkus:dev` verificado: sobe, `GET /chaves` lista as 3 chaves do `import.sql`, Swagger UI responde 200.

## Como rodar
```bash
mvn verify
cd chaves-pix && mvn quarkus:dev     # Dev UI mostra o datasource e as entidades
```

## Próxima aula
O limite de 5 sai do código e vai para a configuração; a aplicação passa a dizer se está viva e pronta, e a medir o cadastro.
