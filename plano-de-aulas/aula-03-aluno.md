# Material do Aluno — Aula 3: Hibernate ORM com Panache

> **Tempo de leitura:** ~20 min. Desligue o dev mode da Aula 2 e suba de novo: as chaves que você cadastrou sumiram, porque moravam num `Map`. Hoje elas vão para um banco (H2 em memória, sem Docker) com **Hibernate ORM** e **Panache**. O Hibernate você já conhece do Spring Data JPA; o Panache é a camada do Quarkus que tira a repetição. O que ele **não** tira é a responsabilidade pela transação, e é aí que mora a maior parte dos defeitos desta aula.
>
> As três pausas de sempre: 🎬 **Imagine o cenário**, 💭 **Pare e reflita** e ✍️ **Experimente agora**.

---

## 1. Por que um banco, e por que a transação importa

> 🎬 **Imagine o cenário**
> Um catálogo de tarifas de serviços (TED, segunda via de cartão, extrato impresso) vive numa planilha carregada em memória a cada subida. O serviço reinicia no meio do dia e a alteração feita de manhã some. Duas pessoas alteram a mesma tarifa ao mesmo tempo. O canal de atendimento consulta o dia inteiro "tarifas vigentes do pacote X" e "quantas tarifas o pacote tem". Onde esses dados deveriam morar? E quem garante que "verificar + gravar" acontece como uma coisa só?

No projeto guiado, o cadastro de uma chave é uma unidade de trabalho com três passos: **a chave é inédita?**, **a conta tem vaga?**, **grave**. Se dois cadastros concorrentes passam pelas duas verificações antes de qualquer um gravar, a regra é furada. O banco, a transação e a restrição de unicidade existem para isso.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (é conceito, não versão).

---

## 2. Datasource sem Dev Services

Duas extensões novas (pelo plugin, como na Aula 2):

```bash
mvn quarkus:add-extension -Dextensions='quarkus-hibernate-orm-panache,quarkus-jdbc-h2'
```

E a configuração, **antes** de subir o dev mode (`gabarito/aula-03/chaves-pix/src/main/resources/application.properties`):

```properties
# Aula 3: H2 em memória. Sem Docker na rede, os Dev Services (banco em contêiner) ficam desligados.
quarkus.devservices.enabled=false
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:chaves;DB_CLOSE_DELAY=-1
quarkus.hibernate-orm.database.generation=drop-and-create
# Dados de exemplo: sem quarkus.hibernate-orm.sql-load-script, o Quarkus carrega o import.sql só em dev e test;
# no jar (perfil prod), não carrega nada.
# Perfil %dev: mostra o SQL só no modo de desenvolvimento.
%dev.quarkus.hibernate-orm.log.sql=true
```

**O que são os Dev Services.** Em dev e test, quando você declara uma extensão de banco e não diz a URL, o Quarkus tenta prover um banco sozinho. Para H2 ele sobe o banco dentro do próprio processo (vimos no log: `Dev Services for H2 started`). Para PostgreSQL, Oracle e outros, ele tenta um **contêiner**, que não existe na rede corporativa. Por isso a pilha do curso **desliga os Dev Services** e declara o H2 explicitamente: o que está no arquivo é o que roda, em qualquer máquina.

**E se você esquecer o datasource com os Dev Services desligados?** O `mvn verify` até pode passar, mas o dev mode não sobe. Saída real:

```
Datasource '<default>' is not configured. To solve this, configure datasource '<default>'. Refer to https://quarkus.io/guides/datasource for guidance.
Press [space] to restart, [e] to edit command line args (currently ''), [r] to resume testing, [o] Toggle test output, [h] for more options>
```

Três detalhes da configuração:

- `DB_CLOSE_DELAY=-1` mantém o banco em memória vivo enquanto a JVM existir, mesmo sem conexão aberta.
- `database.generation=drop-and-create` recria as tabelas a cada subida. Sem os Dev Services, o padrão é `none`: tiramos a linha, o log acusou `Schema-validation: missing table [ChavePix]` e o `GET /chaves` respondeu 500.
- `%dev.` no começo da chave quer dizer "só no perfil dev". Perfis voltam com tudo na Aula 4.
- Não há linha para o `import.sql`, e é de propósito: o padrão de `quarkus.hibernate-orm.sql-load-script` já é `import.sql` em dev e test e **nada** em produção. Escrever `quarkus.hibernate-orm.sql-load-script=import.sql` sem perfil faria os dados de exemplo entrarem também no jar de produção.

> **Da 3.15 até a 3.40** — Na **3.17**, datasource sem URL fica **inativo** e falha na injeção. A configuração explícita deste curso não é afetada.

---

## 3. A entidade Panache

`gabarito/aula-03/chaves-pix/src/main/java/tech/ada/pix/chaves/ChavePix.java`:

```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "valor"}))
public class ChavePix extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TipoChave tipo;

    @Column(nullable = false)
    public String valor;

    @Column(nullable = false)
    public String agencia;

    @Column(nullable = false)
    public String numeroConta;

    @Column(nullable = false)
    public String titularCpf;

    @Column(nullable = false)
    public LocalDateTime criadaEm;
}
```

- `@Entity`, `@Column`, `@Enumerated` são **Jakarta Persistence** (a antiga JPA), as mesmas de sempre. Na 3.15, Hibernate ORM 6.6.
- `PanacheEntity` já traz o `id` (`Long`, gerado por *sequence*). Quem precisa de outro tipo de chave usa `PanacheEntityBase` e declara o próprio `@Id`.
- `EnumType.STRING` grava `'EMAIL'`, não `1` (a posição de `EMAIL` no enum, contando do zero). O padrão (ordinal) grava a posição no enum: reordenar o enum troca o significado dos dados.
- A restrição única em `tipo` + `valor` é a última linha de defesa contra duplicidade: mesmo que duas requisições passem pela verificação do serviço ao mesmo tempo, o banco recusa a segunda.

**Campos públicos não são descuido.** No build, o Quarkus gera getters e setters e **reescreve todo acesso ao campo** para passar por eles. `chave.valor = "x"` vira uma chamada ao setter. Se você escrever um getter próprio, ele é usado.

A tabela que o Hibernate criou, copiada do log do dev mode:

```
create sequence ChavePix_SEQ start with 1 increment by 50
create table ChavePix (
    criadaEm timestamp(6) not null,
    id bigint not null,
    agencia varchar(255) not null,
    numeroConta varchar(255) not null,
    titularCpf varchar(255) not null,
    valor varchar(255) not null,
    tipo enum ('ALEATORIA','CPF','EMAIL','TELEFONE') not null,
    primary key (id),
    unique (tipo, valor)
)
```

> 💭 **Pare e reflita**
> A `ChavePix` da Aula 2 tinha campos privados e 14 métodos de acesso. Esta não tem nenhum. Isso quebra o encapsulamento? Onde fica a fronteira que a API expõe (pista: `ChavePixResponse.de(chave)`)?

> **Da 3.15 até a 3.40** — Na **3.24** chegam o Hibernate ORM 7.0 e o Jakarta Persistence 3.2 (até o ORM 7.4 na 3.37): anotação mal posicionada passa a dar erro. As anotações usadas aqui continuam as mesmas.

---

## 4. Active Record × Repository

O Panache oferece dois estilos. Os dois existem em produção; o projeto guiado usa **Repository**.

| | Active Record | Repository |
|---|---|---|
| Onde ficam as consultas | Métodos estáticos na própria entidade | Numa classe `@ApplicationScoped implements PanacheRepository<T>` |
| Como se chama | `ChavePix.listAll()`, `chave.persist()` | `repository.listAll()`, `repository.persist(chave)` |
| Mais parecido com | Rails, Java EE com entidades "ricas" | Spring Data (o que você usou no módulo anterior) |
| *Mock* no teste do serviço | Exige extensão própria para métodos estáticos | `@InjectMock` no repositório |

O repositório do gabarito (`gabarito/aula-03/.../ChavePixRepository.java`):

```java
@ApplicationScoped
public class ChavePixRepository implements PanacheRepository<ChavePix> {

    public List<ChavePix> findByTitular(String titularCpf) {
        return list("titularCpf", titularCpf);
    }

    public long countByConta(String agencia, String numeroConta) {
        return count("agencia = ?1 and numeroConta = ?2", agencia, numeroConta);
    }

    public boolean existeChave(TipoChave tipo, String valor) {
        return count("tipo = ?1 and valor = ?2", tipo, valor) > 0;
    }
}
```

`persist`, `findByIdOptional`, `listAll` e `deleteById` vêm prontos. Como na Aula 2 o repositório em memória já tinha esses nomes, o **serviço quase não mudou**.

O mesmo `findByTitular` no estilo Active Record, como método estático na entidade (compilado e executado na 3.15.7; não faz parte do gabarito):

```java
// dentro de ChavePix
public static List<ChavePix> doTitular(String titularCpf) {
    return list("titularCpf", titularCpf);
}
```

> **Da 3.15 até a 3.40** — Entre a **3.37 e a 3.39**, o experimento "Panache Next" vira **Quarkus Data** (`quarkus-data-hibernate`). O Panache clássico desta aula continua e não foi depreciado.

---

## 5. Consultas: você escreve, o Panache abrevia

**Não existe consulta derivada do nome do método.** No Spring Data, `findByTitularCpf(String cpf)` numa interface funciona só pela assinatura. No Panache, o repositório é uma **classe**, e `findByTitular` é um método que **você** escreve, chamando `list(...)`, `find(...)` ou `count(...)`.

| Forma | Exemplo | O que é |
|---|---|---|
| Campo = valor | `list("titularCpf", cpf)` | O atalho mais curto |
| HQL simplificado | `count("agencia = ?1 and numeroConta = ?2", ag, conta)` | Só o `where`, sem `select ... from` |
| HQL completo | `find("from ChavePix where tipo = ?1", tipo)` | Quando precisa de mais controle |
| SQL nativo | via `EntityManager` | `find` aceita HQL, **não** SQL nativo |

Atenção a uma diferença de assinatura: no Panache, `findById(id)` devolve a entidade **ou `null`**; quem quer `Optional` usa `findByIdOptional(id)`, como o gabarito.

O SQL que o Hibernate gerou para um `POST /chaves`, copiado do log (`%dev.quarkus.hibernate-orm.log.sql=true`):

```
select count(*) from ChavePix cp1_0 where cp1_0.tipo=? and cp1_0.valor=?
select count(*) from ChavePix cp1_0 where cp1_0.agencia=? and cp1_0.numeroConta=?
select next value for ChavePix_SEQ
insert into ChavePix (agencia, criadaEm, numeroConta, tipo, titularCpf, valor, id) values (?, ?, ?, ?, ?, ?, ?)
```

(No log cada comando sai quebrado em várias linhas; juntamos para caber aqui.) São `existeChave`, `countByConta` e o `persist`, nessa ordem.

> ✍️ **Experimente agora**
> Ligue o log de SQL no seu projeto, faça um `GET /chaves?titular=52998224725` e ache a consulta de `findByTitular` no console. Ela tem `where`? Com qual coluna?

> **Da 3.15 até a 3.40** — Na **3.24**, Hibernate ORM 7 (ver o quadro da seção 3). A forma de escrever consultas Panache não muda.

---

## 6. Transações: `@Transactional` do Jakarta, no serviço

O serviço do gabarito (`gabarito/aula-03/.../ChavePixService.java`, trechos):

```java
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ChavePixService {

    @Transactional
    public ChavePix cadastrar(NovaChaveRequest request) {
        String valor = valorDaChave(request);
        exigirChaveInedita(request.tipo(), valor);
        exigirVagaNaConta(request.agencia(), request.numeroConta());
        ChavePix chave = new ChavePix();
        chave.tipo = request.tipo();
        chave.valor = valor;
        chave.agencia = request.agencia();
        chave.numeroConta = request.numeroConta();
        chave.titularCpf = request.titularCpf();
        chave.criadaEm = LocalDateTime.now();
        repository.persist(chave);
        return chave;
    }

    @Transactional
    public ChavePix trocarConta(Long id, TrocaContaRequest request) {
        ChavePix chave = buscar(id);
        if (!jaEstaNaConta(chave, request.agencia(), request.numeroConta())) {
            // A própria chave não conta no limite: "trocar" para a conta em que ela já está não ocupa vaga nova.
            exigirVagaNaConta(request.agencia(), request.numeroConta());
        }
        chave.agencia = request.agencia();
        chave.numeroConta = request.numeroConta();
        // Sem save(): a entidade está gerenciada e o Hibernate grava a mudança no commit.
        return chave;
    }
}
```

O `@Transactional` é o **`jakarta.transaction.Transactional`** (JTA), não o do Spring. Ele fica no serviço porque é o serviço que conhece a unidade de trabalho "verificar duplicidade + verificar limite + gravar". Leituras (`listar`, `buscar`) funcionam sem transação.

Quatro experimentos, todos feitos no gabarito com o dev mode no ar:

**1. Sem `@Transactional` no `cadastrar`.** O `POST` responde 500:

```
HTTP/1.1 500 Internal Server Error
...
jakarta.persistence.TransactionRequiredException: Transaction is not active, consider adding @Transactional to your method to automatically activate one.
```

**2. `PUT` sem `save()`.** Depois de `PUT /chaves/151` com `{"agencia":"0003","numeroConta":"111111"}`, um `GET /chaves/151` (outra requisição, outra sessão) devolveu a conta nova. A entidade lida dentro da transação está **gerenciada**, e o Hibernate grava a mudança no *commit* (*dirty checking*).

**3. Exceção verificada faz *commit*.** Criamos uma `ChaveEmAnaliseException extends Exception` e a lançamos **depois** do `persist`:

| Anotação | Resposta do `POST` | A chave ficou no banco? |
|---|---|---|
| `@Transactional` | 500 | **Sim** |
| `@Transactional(rollbackOn = ChaveEmAnaliseException.class)` | 500 | Não |

É a regra do JTA: só exceção **não verificada** (`RuntimeException`) desfaz a transação por padrão. As exceções do projeto guiado estendem `ClientErrorException`, que é não verificada: desfazem.

**4. Chamada interna e método privado.** Tiramos o `@Transactional` do `cadastrar` e pusemos o `persist` num método `@Transactional` da mesma classe, chamado de dentro dela:

- método **package-private**: a chave foi gravada (201). No Quarkus, a chamada interna **passa** pelo interceptor;
- método **`private`**: o dev mode nem subiu. O build falhou com:

```
@Transactional will have no effect on method tech.ada.pix.chaves.ChavePixService.gravar() because the method is private. Either remove the annotation from the method, or turn this exception into a simple warning by setting configuration property 'quarkus.arc.fail-on-intercepted-private-method' to 'false'.
```

> 💭 **Pare e reflita**
> Numa regra de negócio de banco, uma exceção verificada do tipo "operação em análise" lançada no fim de um método transacional deixa o registro gravado. Quantas vezes você já viu uma exceção verificada de negócio no código do seu time? Ela desfazia o que devia?

> **Da 3.15 até a 3.40** — Na **3.35**, um endpoint `@Transactional` que retorna `Uni` passa a exigir `@Blocking`. Não afeta este projeto, que só usa retorno comum. As regras de *rollback* e de interceptação não mudam.

---

## 7. `import.sql` e a *sequence*

Dados de exemplo para dev e test (`gabarito/aula-03/chaves-pix/src/main/resources/import.sql`, primeira linha):

```sql
insert into ChavePix (id, tipo, valor, agencia, numeroConta, titularCpf, criadaEm) values (next value for ChavePix_SEQ, 'CPF', '52998224725', '0001', '123456', '52998224725', '2026-10-28 15:00:00');
```

O `id` vem da *sequence*. Resultado real: as três chaves do `import.sql` ganharam os ids **1, 51 e 101**, e o primeiro `POST` ganhou o **151**:

```
HTTP/1.1 201 Created
Location: http://localhost:8080/chaves/151
```

Por que pular de 50 em 50? A *sequence* foi criada com `increment by 50` (seção 3): o Hibernate reserva 50 ids por ida ao banco e distribui em memória.

**A pegadinha do id fixo.** Trocamos o script por um `insert` com `values (1, ...)` e cadastramos duas chaves:

```
1º POST → HTTP/1.1 500 Internal Server Error
2º POST → HTTP/1.1 201 Created
```

E a causa do 500, no log:

```
Caused by: org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation: "PRIMARY KEY ON PUBLIC.CHAVEPIX(ID) ( /* key:1 */ ...
```

O script gravou o id 1 sem passar pela *sequence*; a *sequence* começou em 1 e o primeiro `persist` colidiu. Gere o id pela *sequence*, como o gabarito.

### 7.1. Ids de 50 em 50: três jeitos de gerar o id

O 1, 51, 101 incomoda quem olha a tabela pela primeira vez. Não é defeito: é o preço de uma escolha. Comparamos três formas de gerar o id numa cópia do gabarito da Aula 3 (os números abaixo são saídas reais):

| | (a) Padrão do `PanacheEntity` | (b) `IDENTITY` | (c) *Sequence* própria, de 1 em 1 |
|---|---|---|---|
| Como se declara | nada: `extends PanacheEntity` | `@GeneratedValue(strategy = GenerationType.IDENTITY)` | `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "meu_gerador")` + `@SequenceGenerator(name = "meu_gerador", sequenceName = "minha_sequencia_id", allocationSize = 1)` |
| O que o Hibernate cria | `create sequence ChavePix_SEQ start with 1 increment by 50` | coluna `id` gerada pelo banco | `create sequence minha_sequencia_id start with 1 increment by 1` |
| Ids do `import.sql` (3 linhas) | 1, 51, 101 | 1, 2, 3 | 1, 2, 3 |
| 10 `persist()` seguidos: idas à *sequence* | **1** (reserva 50 de uma vez) | 0 (o banco gera na hora do `insert`) | **10** (uma por chave) |
| Quando o `insert` acontece | no *commit* (ou `flush`) | **na hora do `persist()`**, para o banco devolver o id | no *commit* (ou `flush`) |
| 5 chaves na mesma transação, com *batch* de `insert` ligado | 1 lote com os 5 `insert` | 5 `insert` separados: **sem lote** | 1 lote com os 5 `insert` |
| Transação desfeita (*rollback*) | o id usado (154) se perde | o id usado (7) se perde | o id usado (7) se perde |
| Reinício da aplicação (banco H2 em arquivo) | antes 151, 152; depois **201** | antes 4, 5; depois 6 | antes 4, 5; depois 6 |
| Duas instâncias no mesmo banco, cadastros alternados | 203, **251**, 204, **252** | 8, 9, 10, 11 | 8, 9, 10, 11 |

O que a tabela mostra:

- **(a) troca idas ao banco por buracos.** Cada instância reserva um bloco de 50 e distribui em memória. Reiniciou, o resto do bloco se perde (152 → 201). Duas instâncias, dois blocos: os ids se intercalam e a ordem do id deixa de ser a ordem de cadastro.
- **(b) não pula, mas desliga o lote.** O banco só conhece o id depois do `insert`, então o Hibernate executa o `insert` no `persist()` e não consegue juntar vários num lote. Para cadastro um a um, como o nosso, isso não pesa; para carga de milhares de linhas, pesa.
- **(c) não pula nos casos acima e mantém o lote, mas vai à *sequence* a cada chave.**
- **Nenhuma das três evita buraco no *rollback*:** a *sequence* e a coluna *identity* não devolvem número. Id não é contador de negócio; se o negócio precisa de numeração sem falha (número de protocolo, por exemplo), isso é outro campo, com outra regra.

**E o `import.sql`?** Ele precisa acompanhar a estratégia. Em (a) e (c), o script pede o número à *sequence* (`next value for ChavePix_SEQ` ou `next value for minha_sequencia_id`); em (b), o script **omite** a coluna `id` e o banco preenche:

```sql
insert into ChavePix (tipo, valor, agencia, numeroConta, titularCpf, criadaEm) values ('CPF', '52998224725', '0001', '123456', '52998224725', '2026-10-28 15:00:00');
```

Para usar (b) ou (c), a entidade deixa de estender `PanacheEntity` (que já traz o `id` com a estratégia padrão) e passa a estender `PanacheEntityBase`, com o próprio `@Id`:

```java
@Entity
public class ChavePix extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    // ... os mesmos campos de antes
}
```

O `ChavePixRepository implements PanacheRepository<ChavePix>` continua igual, porque o id segue sendo `Long`.

> 💭 **Pare e reflita**
> Os números acima saíram do H2. Qual banco o seu time usa (Oracle, DB2, PostgreSQL, outro)? Cada um implementa *identity* e *sequence* do seu jeito, e alguns guardam números em cache no próprio banco, o que pode criar buracos que o H2 não mostrou. Antes de escolher a estratégia de um serviço real, descubra o que o banco do seu time faz.

> ✍️ **Experimente agora**
> No seu projeto, troque a `ChavePix` para `PanacheEntityBase` com `@GeneratedValue(strategy = GenerationType.IDENTITY)` e ajuste o `import.sql` (sem a coluna `id`). Suba o dev mode e faça dois `POST`: quais ids saíram? Ligue `%dev.quarkus.hibernate-orm.log.sql=true` e compare o SQL de um `POST` com o da seção 5: sumiu o `select next value`? Depois volte ao `PanacheEntity`.

> **Da 3.15 até a 3.40** — Na **3.23**, `quarkus.hibernate-orm.database.generation` passa a se chamar `quarkus.hibernate-orm.schema-management.strategy` (o nome antigo ainda é aceito por algumas versões). `sql-load-script` não muda. Na **3.24** (Hibernate ORM 7.0), combinar `GenerationType.SEQUENCE` com um gerador que não seja `@SequenceGenerator` passa a ser erro (guia de migração do Hibernate ORM 7.0); as três formas da seção 7.1 não são afetadas.

---

## 8. Testes com banco

O banco de teste é o mesmo para a suíte inteira: o `@QuarkusTest` sobe a aplicação uma vez. Teste que conta registros passa sozinho e falha junto com os outros, se dois testes mexerem na mesma conta. O gabarito resolve de dois jeitos:

- o `ChavePixResourceTest` gera uma conta nova por teste;
- o `ChavePixRepositoryTest` só lê a conta `0001/123456` do `import.sql`, em que nenhum outro teste escreve.

Trecho de `gabarito/aula-03/.../ChavePixRepositoryTest.java`:

```java
@QuarkusTest
class ChavePixRepositoryTest {

    @Inject
    ChavePixRepository repository;

    @Test
    void countByContaContaAsChavesDoImportSql() {
        assertThat(repository.countByConta("0001", "123456"), is(2L));
    }
}
```

Repare: o teste **injeta o repositório** com `@Inject`, como qualquer bean. Saída real do `mvn verify`:

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, ... in tech.ada.pix.chaves.ChavePixRepositoryTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, ... in tech.ada.pix.chaves.ChavePixResourceTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 nos testes com banco (a renomeação de `quarkus-junit5` na 3.31 está no quadro da Aula 1).

---

## 9. No Spring era assim

| No Spring Boot (Spring Data JPA) | No Quarkus 3.15 (Panache) |
|---|---|
| `interface ChaveRepository extends JpaRepository<ChavePix, Long>` | `class ChavePixRepository implements PanacheRepository<ChavePix>`, `@ApplicationScoped` |
| Consulta derivada do nome (`findByTitularCpf`) | **Não existe**: escreva o método com `list(...)`/`count(...)` |
| `findById(id)` → `Optional<T>` | `findById(id)` → `T` ou `null`; `findByIdOptional(id)` → `Optional<T>` |
| `save(entity)` | `persist(entity)`; entidade gerenciada grava no *commit* sem chamar nada |
| `@Query("select ...")` | `find("from ChavePix where ...", ...)` |
| Entidade com `@Id @GeneratedValue` e getters/setters | `extends PanacheEntity`, campos públicos |
| `spring.jpa.hibernate.ddl-auto` | `quarkus.hibernate-orm.database.generation` |
| `spring.jpa.show-sql` | `quarkus.hibernate-orm.log.sql` |
| `data.sql` | `import.sql` |
| `org.springframework.transaction.annotation.Transactional` | `jakarta.transaction.Transactional` |
| `rollbackFor` | `rollbackOn` |
| Chamada interna (`this.metodo()`) **não** passa pelo proxy | Chamada interna **passa** pelo interceptor |
| `@Transactional` em método privado é ignorado em silêncio | `@Transactional` em método privado **quebra o build** |

> **Da 3.15 até a 3.40** — as mudanças desta tabela estão nos quadros das seções 2, 3, 4 e 7.

---

**✍️ Desafio da aula**

1. Tenha o `chaves-pix` com H2 e Panache rodando (copie do `gabarito/aula-03/` se precisar).
2. Acrescente ao repositório `findByTipo(TipoChave tipo)` e exponha `GET /chaves?tipo=EMAIL`, combinável com `titular`, com teste.
3. Escreva um teste no `ChavePixRepositoryTest` para `countByConta` numa conta que **o próprio teste** criou.
4. Aprofundamento (opcional): refaça o experimento da exceção verificada (seção 6, item 3) no seu projeto e explique o resultado para alguém do grupo, com e sem `rollbackOn`.

**Para ir além** (guias da versão 3.15)

- Panache (Active Record, Repository, consultas, transação): https://quarkus.io/version/3.15/guides/hibernate-orm-panache
- Hibernate ORM no Quarkus (`database.generation`, `import.sql`, log de SQL): https://quarkus.io/version/3.15/guides/hibernate-orm
- Datasources (H2, URL, Dev Services): https://quarkus.io/version/3.15/guides/datasource
- Transações (`@Transactional`, `rollbackOn`, `QuarkusTransaction`): https://quarkus.io/version/3.15/guides/transaction

**Para levar ao projeto final**

- Entidades e repositórios do serviço principal, com H2, `import.sql` (id pela *sequence*) e um teste de repositório.
- Registre no README do grupo: Active Record ou Repository, e por quê.
- `@Transactional` do Jakarta nos métodos de escrita do serviço; exceções de negócio não verificadas (ou `rollbackOn`).
- Restrição única no banco para o que não pode duplicar.
- Quem está portando o projeto do módulo anterior: liste as consultas derivadas do Spring Data que viraram método escrito.

> **Na próxima aula:** o limite de 5 chaves por conta está numa constante. Se a regra mudar amanhã, quanto tempo leva para chegar à produção? E como a plataforma sabe que o serviço está **pronto**, e não só **vivo**?
