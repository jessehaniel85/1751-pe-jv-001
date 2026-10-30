# Leitura complementar da Aula 2 — CDI com ArC: instâncias, escopos e requisições concorrentes

> **Leitura opcional, ~20 min.** Aprofunda a seção 6 do material da Aula 2 ("CDI com ArC: escopos e injeção"). As perguntas que guiam o texto: quantas instâncias de um bean existem, quem as cria, o que acontece quando muitas requisições chegam ao mesmo tempo, quando usar cada escopo e onde entra a idempotência.
>
> Todo o código citado vem de um **projeto de demonstração** (`demo-cdi`, pacote `tech.ada.demo.cdi`), compilado e testado no Quarkus 3.15.7 com Java 21. Ele **não** faz parte do gabarito. As saídas mostradas são de execuções reais da suíte (`mvn verify`: 12 testes, 0 falhas).
>
> As pausas de sempre: 🎬 **Imagine o cenário**, 💭 **Pare e reflita** e ✍️ **Experimente agora**.

---

## 1. Bean e contêiner

No CDI (Jakarta Contexts and Dependency Injection), um **bean** é um objeto gerenciado pelo contêiner: ele recebe injeção de dependências, tem *callbacks* de ciclo de vida (`@PostConstruct`, `@PreDestroy`) e pode ser interceptado (é assim que `@Transactional` funciona). O **contêiner** cria as instâncias, injeta, associa cada instância a um contexto (aplicação, requisição...) e as destrói quando o contexto termina. Você nunca dá `new` num bean.

Como o contêiner sabe que uma classe é bean? Pela **anotação que define bean** (*bean-defining annotation*): uma anotação de escopo (`@ApplicationScoped`, `@RequestScoped`, `@Dependent`, `@Singleton`...) ou um estereótipo. Classe sem nenhuma delas **não vira bean** no Quarkus. Fizemos o teste: uma classe `SemAnotacao`, só com um campo `@Inject`, injetada num serviço. O build parou:

```
Unsatisfied dependency for type tech.ada.demo.cdi.SemAnotacao and qualifiers [@Default]
	- injection target: tech.ada.demo.cdi.UsaSemAnotacao#sem
	The following classes match by type, but have been skipped during discovery:
	- tech.ada.demo.cdi.SemAnotacao has no bean defining annotation (scope, stereotype, etc.)
```

**CDI Lite × CDI Full, em uma frase:** a especificação tem um subconjunto menor, o **CDI Lite**, pensado para ambientes mais restritos e para resolução no build, e o **CDI Full**, que acrescenta recursos avançados e é o que os servidores de aplicação Jakarta EE implementam; o ArC implementa o CDI Lite (e passa no TCK dele), com extras próprios, e não implementa o CDI Full.

> **Ponte do legado.** Num WildFly ou WebLogic com `beans.xml` em `bean-discovery-mode="all"`, toda classe do arquivo virava bean `@Dependent`, com ou sem anotação. No Quarkus a descoberta é sempre a do modo `annotated`, e o conteúdo do `beans.xml` é ignorado.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 na descoberta de beans (guia de referência do CDI, versões 3.15 e 3.40).

---

## 2. Os escopos

O **escopo** diz quantas instâncias existem e quanto tempo cada uma vive. Todo bean tem exatamente um.

| Escopo | Instâncias | Nasce | Morre | Injetado por *client proxy*? | `@InjectMock` |
|---|---|---|---|---|---|
| `@ApplicationScoped` | **Uma** para a aplicação | Na 1ª chamada de método pelo proxy | No desligamento da aplicação | Sim | Sim |
| `@Singleton` | **Uma** para a aplicação | Quando é injetado pela 1ª vez | No desligamento da aplicação | **Não** | Não, por padrão (ver abaixo) |
| `@RequestScoped` | Uma **por requisição** | Na 1ª chamada de método dentro da requisição | No fim da requisição | Sim | Sim |
| `@Dependent` | Uma **por ponto de injeção** | Junto com o bean que a recebe | Junto com o bean que a recebe | Não | Não |
| `@SessionScoped` | Uma por sessão HTTP | Na 1ª chamada dentro da sessão | No fim da sessão | Sim | — |

O `@SessionScoped` está na tabela só para situar: no Quarkus ele depende da extensão de *servlet* (Undertow), e uma API REST sem estado de sessão, como a do projeto guiado, não o usa.

### Escopo normal × pseudoescopo

- **Escopo normal** (`@ApplicationScoped`, `@RequestScoped`, `@SessionScoped`): quem injeta recebe um **client proxy**, nunca a instância.
- **Pseudoescopo** (`@Singleton`, `@Dependent`): quem injeta recebe a **própria instância**.

### O *client proxy*

O proxy é uma classe gerada pelo ArC no build. Ele tem os mesmos métodos públicos do bean, e cada chamada faz uma coisa só: **localiza a instância certa no contexto e encaminha a chamada para ela**. Para `@ApplicationScoped`, "a instância certa" é sempre a mesma; para `@RequestScoped`, é a da requisição em curso.

Conferimos no projeto de demonstração. O recurso `DiagnosticoCdiResource` injeta um bean de cada escopo e devolve o nome da classe recebida e um identificador gerado no `@PostConstruct` (`ContadorDeConsultas.java` e `RegistroDeTipos.java`, trechos):

```java
@ApplicationScoped
public class ContadorDeConsultas {

    private String instancia;
    // ...

    @PostConstruct
    void iniciar() {
        instancia = UUID.randomUUID().toString();
    }
    // ...
}
```

```java
/** Também uma instância só, mas injetada sem proxy. */
@Singleton
public class RegistroDeTipos {
    // mesmo @PostConstruct, mesmo campo instancia
}
```

Saída dos testes (`EscoposTest`):

```
[demo] @ApplicationScoped injetado como: tech.ada.demo.cdi.ContadorDeConsultas_ClientProxy
[demo] @Singleton injetado como: tech.ada.demo.cdi.RegistroDeTipos
```

Duas requisições seguidas a `/cdi/application` devolvem o **mesmo** identificador: uma instância só, alcançada pelo proxy. O mesmo vale para `/cdi/singleton`, sem proxy.

O proxy traz três consequências práticas:

1. **Criação preguiçosa.** A instância de um `@ApplicationScoped` só nasce na primeira chamada de método. Injetar não basta. Para criar na subida, observe o evento de partida (`void iniciar(@Observes StartupEvent ev)`) ou use `@io.quarkus.runtime.Startup`.
2. **O proxy só encaminha métodos.** Nunca leia nem escreva **campo** de um bean de escopo normal injetado (`servico.algumCampo`): você estaria lendo o campo do proxy, não da instância. Use métodos.
3. **Um bean de escopo "mais curto" pode ser injetado num de escopo "mais longo".** Um `@ApplicationScoped` pode injetar um `@RequestScoped`: o proxy entrega, a cada chamada, a instância da requisição corrente (seção 2.2 abaixo).

### 2.1. `@Singleton`: quando faz sentido

`@Singleton` é um escopo legítimo de produção. Ele é "como o `@ApplicationScoped`, mas sem proxy", e o guia do Quarkus lista as diferenças:

- é um pouco mais barato, porque não há o desvio pelo proxy;
- nasce quando é injetado, não na primeira chamada;
- campos podem ser lidos diretamente (não há proxy no meio);
- **não pode ser trocado por *mock*** com `@InjectMock` ou `QuarkusMock`, porque não há proxy onde trocar a instância. Existe uma saída, `@MockitoConfig(convertScopes = true)`, que o guia chama de opção avançada: ela converte o bean em `@ApplicationScoped` só durante o teste.

Tentamos instalar um *mock* nos dois escopos com `QuarkusMock.installMockForType(...)`. No `@ApplicationScoped`, o *mock* entrou no lugar da instância. No `@Singleton`:

```
[demo] QuarkusMock em @Singleton: tech.ada.demo.cdi.RegistroDeTipos@7827d7b is not a normal scoped CDI bean,
       make sure the bean is a normal scope like @ApplicationScoped or @RequestScoped
```

A recomendação do guia é usar `@ApplicationScoped` por padrão e `@Singleton` quando houver um motivo. Bons motivos: um bean que você nunca vai substituir em teste (um registro de constantes, um conversor sem dependências) e cujo custo de chamada importa. No projeto guiado, serviço e repositório ficam em `@ApplicationScoped` porque na Aula 5 o teste do serviço troca dependências por *mock*.

### 2.2. `@RequestScoped`: dados que pertencem a uma requisição

`@RequestScoped` dá uma instância por requisição. No JSF, `@RequestScoped` e `@SessionScoped` apareciam nos *managed beans* das telas; no CDI eles são escopos gerais, e numa API REST o `@RequestScoped` é o lugar natural para **dados que pertencem àquela requisição**:

- a identidade de quem chamou: o `SecurityIdentity` do Quarkus Security é um bean `@RequestScoped` (conferido no projeto de demonstração) e é o que usaremos na Aula 8 para a regra "o titular só mexe nas próprias chaves";
- o *token* JWT: o guia de JWT do Quarkus força o escopo de requisição no `JsonWebToken` injetado, para que ele represente sempre o *token* da chamada corrente;
- um identificador de correlação para os *logs*, o *tenant* de um sistema multiempresa, o canal de origem (app, internet banking, atendimento).

Exemplo do projeto de demonstração: um filtro lê o cabeçalho `X-Correlation-Id` (ou gera um id) e o guarda num bean de requisição; um serviço `@ApplicationScoped` o usa sem receber nada por parâmetro (`ContextoDaRequisicao.java`, `FiltroDeCorrelacao.java` e `AuditoriaService.java`):

```java
/** Dados que pertencem a uma requisição: nascem com ela e morrem com ela. */
@RequestScoped
public class ContextoDaRequisicao {

    private String idCorrelacao;

    public void definirIdCorrelacao(String recebido) {
        idCorrelacao = (recebido == null || recebido.isBlank()) ? UUID.randomUUID().toString() : recebido;
    }

    public String idCorrelacao() {
        return idCorrelacao;
    }
}
```

```java
/** Lê o cabeçalho X-Correlation-Id (ou gera um id) e guarda no bean da requisição. */
public class FiltroDeCorrelacao {

    @Inject
    ContextoDaRequisicao contexto;

    @ServerRequestFilter
    public void preencher(ContainerRequestContext requisicao) {
        contexto.definirIdCorrelacao(requisicao.getHeaderString("X-Correlation-Id"));
    }
}
```

```java
/** Bean de aplicação que injeta um bean de requisição: o proxy entrega o da requisição corrente. */
@ApplicationScoped
public class AuditoriaService {

    @Inject
    ContextoDaRequisicao contexto;

    public String registrar(String operacao) {
        return operacao + " [correlação " + contexto.idCorrelacao() + "]";
    }
    // ...
}
```

O que os testes comprovaram:

- dentro de uma requisição, o recurso e o `AuditoriaService` enxergam **o mesmo** id;
- duas requisições seguidas têm ids **diferentes**;
- com o cabeçalho `X-Correlation-Id: pix-123`, o serviço registra `consulta [correlação pix-123]`.

Repare que o `AuditoriaService` é **uma** instância para a aplicação inteira e, mesmo assim, cada requisição vê o próprio id: quem guarda o dado é o bean de requisição, e o proxy escolhe a instância da requisição corrente a cada chamada.

O que **não** vai em `@RequestScoped`: dados que precisam sobreviver à requisição. A seção 6 da Aula 2 mostrou o efeito: com o repositório em `@RequestScoped`, a chave criada no `POST` some antes do `GET`.

> 💭 **Pare e reflita**
> O `ContextoDaRequisicao` é preenchido no filtro e lido no serviço. Se uma chamada de método acontecer **fora** de uma requisição (num `@Observes StartupEvent`, por exemplo), o que você espera que aconteça? Pista: procure "request context" no guia de referência do CDI.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 nos escopos, no *client proxy* e na regra de *mock* do `@Singleton` (`@MockitoConfig(convertScopes = true)` continua no guia de testes da 3.40).

---

## 3. "Padrão como?": o `@Dependent`

`@Dependent` é o escopo que um bean recebe **quando nenhum escopo é declarado** (regra da especificação). No Quarkus, isso acontece em três situações, todas conferidas no projeto de demonstração:

| Situação | Escopo resultante |
|---|---|
| Classe anotada com `@Dependent` explicitamente | `@Dependent` |
| Método ou campo **produtor** (`@Produces`) sem anotação de escopo | `@Dependent` |
| Classe **sem anotação** que só declara um produtor ou um observador (`@Observes`) | a classe vira bean `@Dependent` |

Classe sem anotação nenhuma, e sem produtor nem observador, não vira bean (seção 1). Uma exceção do próprio Quarkus: `@Startup` numa classe sem escopo faz dela `@ApplicationScoped` (conferido no experimento).

As regras do `@Dependent`:

- **uma instância nova por ponto de injeção**, nunca compartilhada;
- a instância é um **objeto dependente** de quem a recebeu: nasce quando o dono é criado e é destruída quando o dono é destruído (com `@PreDestroy`);
- é um pseudoescopo: injeta-se a própria instância, sem proxy.

Exemplo (`FormatadorDeChave.java`, `ConsultaDeChaves.java`, `CadastroDeChaves.java` e `RelogioProducer.java`, trechos):

```java
/** Uma instância nova para cada ponto de injeção; vive e morre com quem a recebeu. */
@Dependent
public class FormatadorDeChave {

    private final String instancia = UUID.randomUUID().toString();

    public String mascarar(String valor) {
        return valor.length() <= 4 ? "****" : "****" + valor.substring(valor.length() - 4);
    }
    // ...
}
```

```java
@Servico
public class ConsultaDeChaves {

    @Inject
    FormatadorDeChave formatador;
    // ...
}

@ApplicationScoped
public class CadastroDeChaves {

    @Inject
    FormatadorDeChave formatador;

    @Inject
    Clock relogio;
    // ...
}
```

```java
/** Classe sem anotação de escopo: só o método produtor é descoberto. */
public class RelogioProducer {

    @Produces
    Clock relogio() {                       // sem escopo declarado: @Dependent
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
```

O teste `dependentDaUmaInstanciaPorPontoDeInjecao` confirma que `ConsultaDeChaves` e `CadastroDeChaves` recebem formatadores **diferentes**. O teste `escoposDeclaradosEPadrao` pergunta ao contêiner o escopo de cada bean: `Clock` (produtor sem escopo) e `RelogioProducer` (classe sem anotação) saem `@Dependent`. (`@Servico` é um estereótipo próprio, explicado na seção 7.)

**Para quem vem do Spring.** O `@Dependent` equivale ao `prototype` do Spring, com uma diferença: no Spring, o contêiner não chama os *callbacks* de destruição de um `prototype`; no CDI, o `@Dependent` é destruído junto com o dono. E a **armadilha** é o padrão: no Spring, bean sem `@Scope` é `singleton`; no CDI, produtor sem escopo é `@Dependent`. Um `@Bean` "caro" (um cliente HTTP, um *pool*) convertido para `@Produces` sem escopo vira uma instância **por ponto de injeção**. Se a intenção era uma só, declare `@ApplicationScoped` (ou `@Singleton`) no método produtor.

> ✍️ **Experimente agora**
> No seu projeto, crie um produtor de `Clock` sem escopo e injete-o em dois beans. Escreva um teste que compare `System.identityHashCode(...)` das duas instâncias. Depois anote o produtor com `@Singleton` e rode de novo.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no escopo padrão.

---

## 4. ArC: o CDI decidido no build

O ArC faz no build o que um servidor de aplicação faz na subida: descobre os beans, liga cada ponto de injeção a um bean e gera os proxies. O que muda para você:

- **Injeção não satisfeita quebra o build** (exemplo na seção 1).
- **Injeção ambígua também.** Duas implementações `@ApplicationScoped` de uma interface `Notificador`, injetada sem qualificador:

  ```
  AmbiguousResolutionException: Ambiguous dependencies for type tech.ada.demo.cdi.Notificador and qualifiers [@Default]
  	- injection target: tech.ada.demo.cdi.UsaNotificador#notificador
  ```

  Com `@Alternative` e `@Priority(1)` numa delas, o build passou e a escolhida foi injetada (seção 7).
- **Beans não usados são removidos** (seção 6 da Aula 2): `@io.quarkus.arc.Unremovable` impede a remoção.

**Diferenças em relação à especificação que importam aqui:**

| Tema | No CDI de um servidor de aplicação (CDI Full) | No Quarkus (ArC) |
|---|---|---|
| Descoberta | Conforme o `beans.xml` (`all`, `annotated`) | Sempre `annotated`; conteúdo do `beans.xml` ignorado |
| Erros de injeção | Na implantação (*deploy*) | No build |
| Bean não usado | Continua existindo | Removido no build |
| Autoinvocação (`this.metodo()`) | Não passa pelos interceptadores | **Passa** (Aula 3, `@Transactional`) |
| Concorrência gerenciada | — | `@io.quarkus.arc.Lock` (seção 5) |
| `@SessionScoped` | Disponível | Só com a extensão de *servlet* (Undertow) |

### Qual o escopo de um recurso REST sem anotação?

O `ChavePixResource` da Aula 2 não declara escopo. No Quarkus REST, um recurso sem anotação de escopo é **`@Singleton`**: uma instância para a aplicação, sem proxy. Conferimos de duas formas no `DiagnosticoCdiResource`, que também não declara escopo:

```
[demo] escopo do recurso REST sem anotação: jakarta.inject.Singleton
```

e duas requisições a `/cdi/recurso` devolvem o mesmo `System.identityHashCode(this)`.

> **Ponte do legado.** A especificação Jakarta REST diz que, **por padrão**, cada requisição ganha uma instância nova da classe de recurso. Quem vem do RESTEasy num WildFly está acostumado a isso. No Quarkus REST o padrão é `@Singleton`. Para voltar ao comportamento antigo, anote o recurso com `@RequestScoped`: no experimento, três requisições deram três instâncias diferentes.

Consequência: o recurso é compartilhado por todas as requisições, exatamente como um serviço `@ApplicationScoped`. Campo de recurso só para dependências injetadas, nunca para dado da requisição. Injetar um bean `@RequestScoped` no recurso, como o `DiagnosticoCdiResource` faz com o `ContextoDaRequisicao`, funciona: o que o recurso guarda é o proxy.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 na validação do build e na remoção de beans. Entre a 3.15 e a 3.40, a lista de "recursos não padronizados" do guia de referência ganhou dois itens (ordenação de beans na busca programática e o comportamento de `Instance.Handle.close()`), nenhum usado neste módulo.

---

## 5. Requisições e threads

> 🎬 **Imagine o cenário**
> Às 10h de um dia útil, 200 aplicativos consultam e cadastram chaves ao mesmo tempo. O `ChavePixService` é `@ApplicationScoped`. Quantos objetos `ChavePixService` existem na memória nesse instante? E se dois cadastros da **mesma** chave chegarem no mesmo milissegundo?

### Quem atende a requisição

A seção 5 da Aula 2 mostrou a regra pela assinatura do método. O caminho completo:

```
 conexão HTTP ──► thread de I/O do Vert.x (event loop, poucas threads)
                     │
                     ├─ método devolve Uni/Multi/CompletionStage ──► roda ali mesmo, sem bloquear
                     │
                     └─ método devolve tipo comum ──► worker thread (pool "executor-thread-N")
                                                       pode bloquear: JDBC, HTTP síncrono
```

- O Quarkus **sempre** recebe a requisição numa thread de I/O e troca para uma *worker thread* quando o código é bloqueante.
- `@Blocking` e `@NonBlocking` (pacote `io.smallrye.common.annotation`) mudam essa escolha no método, na classe ou na aplicação. `@Transactional` também faz o método ser tratado como bloqueante.
- `@RunOnVirtualThread` (Java 21+) roda um método bloqueante numa **thread virtual** em vez de uma *worker thread*. Fica só como referência: este módulo não usa threads virtuais.
- Para cada requisição HTTP, o Quarkus **ativa um contexto de requisição**: é ele que guarda as instâncias `@RequestScoped` daquela requisição e as destrói no fim.

### Uma instância, muitas threads

Um bean `@ApplicationScoped` tem **uma** instância, e **todas** as threads chamam essa mesma instância ao mesmo tempo. O proxy não cria cópias nem distribui instâncias: ele só encaminha a chamada para o único objeto que existe. Não há *pool* de instâncias no CDI.

> **Ponte do legado.** No EJB, o `@Stateless` funcionava com um *pool*: o contêiner mantinha várias instâncias e cada chamada usava uma delas com exclusividade, por isso um campo de instância não era disputado por duas chamadas ao mesmo tempo. O `@ApplicationScoped` é diferente: é mais parecido com o `@Singleton` do EJB, uma instância compartilhada e acessada concorrentemente, mas **sem** o bloqueio que o contêiner EJB aplicava por padrão.

Medimos com 8 threads chamando o mesmo `ContadorDeConsultas`. O método soma em quatro contadores, dois inseguros e dois seguros (`ContadorDeConsultas.java`, trecho):

```java
    // Inseguros: leitura e escrita separadas, sem sincronização.
    private int totalInseguro;
    private final Map<String, Integer> porTipoInseguro = new HashMap<>();

    // Seguros: operações atômicas.
    private final AtomicInteger totalSeguro = new AtomicInteger();
    private final Map<String, Integer> porTipoSeguro = new ConcurrentHashMap<>();
    // ...

    public void registrar(String tipo) {
        totalInseguro++;                                                   // lê, soma, grava: 3 passos
        porTipoInseguro.put(tipo, porTipoInseguro.getOrDefault(tipo, 0) + 1);

        totalSeguro.incrementAndGet();                                     // 1 passo atômico
        porTipoSeguro.merge(tipo, 1, Integer::sum);                        // atômico no ConcurrentHashMap
    }
```

Resultados de uma execução (`ConcorrenciaTest`; os números dos inseguros mudam a cada execução):

```
[demo] direto: esperado=2000000 int=1976721 HashMap=1955700 AtomicInteger=2000000 ConcurrentHashMap=2000000
[demo] HTTP: requisicoes=2000 threadsDoServidor=8 instancias=1 int=1988 AtomicInteger=2000
```

- **Direto:** 8 threads × 250.000 chamadas. O `int` e o `HashMap` **perderam** atualizações; `AtomicInteger` e `ConcurrentHashMap` acertaram.
- **Por HTTP:** 8 clientes × 250 `POST /cdi/consultas/EMAIL`. As requisições foram atendidas por **8 worker threads** diferentes, e **todas** viram **a mesma instância** do bean (`instancias=1`). O `int` perdeu 12 de 2.000.

O teste só **exige** o resultado dos contadores seguros. Os inseguros **podem** perder, e quanto perdem depende da máquina e do momento; o teste apenas registra o valor.

### Atômico em cada passo não é atômico na sequência

O `ConcurrentHashMap` do repositório da Aula 2 garante cada operação, isoladamente. Uma regra de negócio que **verifica e depois grava** são duas operações, e outra thread pode passar entre elas. No projeto de demonstração (`DiretorioEmMemoria.java`, trecho):

```java
    /** Duas operações seguras em sequência: a sequência não é atômica. */
    public boolean cadastrarVerificandoAntes(String valor) {
        if (porId.containsValue(valor)) {                 // 1. verifica
            return false;
        }
        porId.put(sequencia.incrementAndGet(), valor);    // 2. grava
        return true;
    }

    /** Verifica e grava numa operação só. */
    public boolean cadastrarAtomico(String valor) {
        return porValor.putIfAbsent(valor, sequencia.incrementAndGet()) == null;
    }
```

Oito threads tentando cadastrar **a mesma** chave no mesmo instante, 2.000 vezes (`VerificarEGravarTest`, uma execução):

```
[demo] rodadas=2000 com chave duplicada: verificar-e-gravar=762 putIfAbsent=0
```

A regra "chave inédita" do `ChavePixService` da Aula 2 tem esse mesmo formato (`existeChave` e depois `persist`). Em memória, é uma simplificação da aula. Na Aula 3, a restrição de unicidade do banco (`@UniqueConstraint` em tipo + valor) passa a recusar a segunda gravação, qualquer que seja a thread.

### As requisições se misturam?

Não, desde que o bean compartilhado **não guarde estado da requisição em campo**. A regra prática:

| O dado é... | Onde mora |
|---|---|
| Da requisição (o corpo, o CPF de quem chamou, a chave sendo cadastrada) | Parâmetros e variáveis locais (cada thread tem a sua pilha) ou um bean `@RequestScoped` |
| Compartilhado de propósito (cache, contador, o `Map` do repositório em memória) | Campo de bean `@ApplicationScoped`, com estrutura *thread-safe* (`ConcurrentHashMap`, `Atomic*`) |
| Persistente | Banco (Aula 3), que trata a concorrência com transações e restrições |
| Dependências (outros beans, configuração) | Campos injetados; eles não mudam depois da criação |

Se precisar mesmo de exclusão mútua num bean compartilhado, o ArC oferece `@io.quarkus.arc.Lock` (sem parâmetro, é bloqueio de escrita: uma chamada por vez; `@Lock(Lock.Type.READ)` libera leituras simultâneas). Use com parcimônia: serializar as chamadas de um serviço serializa a API.

> 💭 **Pare e reflita**
> O `ChavePixService` da Aula 2 tem um campo: `ChavePixRepository repository`. Por que esse campo **não** é um problema de concorrência, e o `int totalInseguro` do exemplo é?

> **Da 3.15 até a 3.40** — Na **3.35**, um endpoint `@Transactional` que retorna `Uni` passa a exigir `@Blocking` (mesmo quadro da seção 5 da Aula 2). Sem mudança relevante até a 3.40 no compartilhamento de instâncias e no `@Lock`.

---

## 6. Estado compartilhado × idempotência

São duas perguntas diferentes, que costumam aparecer juntas:

- **Estado compartilhado:** duas requisições **simultâneas** atrapalham uma à outra dentro do processo? É assunto de escopo e de *thread-safety* (seção 5).
- **Idempotência:** repetir **a mesma** requisição produz o mesmo efeito no servidor que fazê-la uma vez? É assunto do **desenho da operação**, e vale mesmo que as repetições cheguem com minutos de intervalo, em instâncias diferentes do serviço.

### Idempotência em HTTP

A RFC 9110 define: um método é idempotente se o efeito pretendido de várias requisições idênticas é o mesmo de uma só. `GET`, `HEAD`, `OPTIONS`, `PUT` e `DELETE` são idempotentes; **`POST` não é**. A mesma RFC diz que o cliente **não deve** repetir sozinho uma requisição não idempotente, salvo se tiver como saber que ela é idempotente na prática ou que a original não foi aplicada.

No projeto guiado:

| Requisição repetida | 1ª vez | 2ª vez | Efeito no servidor |
|---|---|---|---|
| `PUT /chaves/1` com a mesma conta | 200 | 200 | O mesmo: a chave está na conta pedida |
| `DELETE /chaves/2` | 204 | 404 | O mesmo: a chave não existe. O status muda, o efeito não |
| `POST /chaves` de um e-mail | 201 | **409** | Uma chave só, graças à regra de unicidade |
| `POST /chaves` de uma chave **aleatória** | 201 | **201** | **Duas** chaves: o servidor gera um UUID novo a cada vez |

> 🎬 **Imagine o cenário**
> O aplicativo manda `POST /chaves` para cadastrar uma chave aleatória. A resposta demora, o celular troca de rede e a chamada estoura o tempo. O aplicativo não sabe se a chave foi criada e tenta de novo. Resultado: o cliente ficou com duas chaves aleatórias e ocupou duas das 5 vagas da conta. E no caso do e-mail, a 2ª tentativa recebe 409: o aplicativo mostra "erro" para uma operação que deu certo.

### Como uma API trata isso

1. **Unicidade de negócio.** Quando o próprio pedido tem uma identidade natural (tipo + valor da chave), a regra de unicidade impede a duplicata. É o que o projeto faz com 409. Do lado do cliente, 409 numa nova tentativa significa "já existe": ele pode consultar (`GET /chaves?titular=...`) e seguir.
2. **Chave de idempotência.** Quando o pedido não tem identidade natural (a chave aleatória, uma transferência), o cliente gera um identificador único por **intenção** e o envia em toda tentativa, num cabeçalho `Idempotency-Key`. O servidor guarda a chave com o resultado e, ao ver a mesma chave de novo, devolve o resultado guardado em vez de executar outra vez. O cabeçalho está em padronização no IETF (*Internet-Draft* `draft-ietf-httpapi-idempotency-key-header`, versão 08, ainda não é RFC); o rascunho propõe 409 se a repetição chegar enquanto a original ainda está em processamento e 422 se a mesma chave vier com um corpo diferente.

### O escopo do bean não garante idempotência

Nenhum escopo torna uma operação idempotente:

- um serviço `@ApplicationScoped` sem estado mutável, perfeito do ponto de vista de concorrência, cria duas chaves aleatórias se receber dois `POST`;
- trocar para `@RequestScoped` também não ajuda: cada requisição ganha uma instância nova, e a repetição não tem como "lembrar" da original;
- a "memória" de que a operação já foi feita precisa estar num lugar que **sobreviva à requisição e seja visto por todas as instâncias do serviço**: o banco, com uma restrição de unicidade sobre o valor da chave ou sobre a chave de idempotência.

Bean sem estado ajuda no **isolamento** entre requisições simultâneas. Idempotência é **desenho da operação**: identidade do pedido, restrição de unicidade e resposta coerente para a repetição.

> 💭 **Pare e reflita**
> Se a API guardasse as chaves de idempotência num `ConcurrentHashMap` de um bean `@ApplicationScoped`, o que aconteceria no dia em que o serviço rodasse em **duas** instâncias atrás de um balanceador? E depois de um reinício?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40: idempotência é desenho de API, não versão do Quarkus.

---

## 7. No Spring era assim

| No Spring | No Quarkus (CDI) | Observação |
|---|---|---|
| `@Component`, `@Service`, `@Repository` | `@ApplicationScoped` | O papel fica no nome da classe (Aula 2, seção 6) |
| `@Scope("singleton")` (o padrão) | `@ApplicationScoped`, ou `@Singleton` se não precisar de proxy nem *mock* | |
| `@Scope("prototype")` | `@Dependent` | No CDI é destruído com o dono (seção 3) |
| `@Scope("request")` / `@Scope("session")` | `@RequestScoped` / `@SessionScoped` | `@SessionScoped` só com Undertow |
| `@Autowired` | `@Inject` | Construtor único dispensa `@Inject` |
| `@Configuration` + `@Bean` | Classe com método `@Produces` | **Produtor sem escopo é `@Dependent`**, não singleton |
| `@Primary` | `@Alternative` + `@Priority` no bean preferido; ou `@io.quarkus.arc.DefaultBean` no bean que cede | |
| `@Qualifier("pix")` | `@Named("pix")` ou um qualificador próprio (anotação com `@Qualifier` do `jakarta.inject`) | |
| `@Lazy` | Já é o comportamento dos escopos normais | Para criar na subida: `@Observes StartupEvent` ou `@Startup` |
| Singleton criado na subida (padrão do Spring) | `@ApplicationScoped` criado na 1ª chamada | |
| `@PostConstruct`, `@PreDestroy` | Os mesmos (`jakarta.annotation`) | |

**Não existe um `@Component` genérico no CDI.** No Spring, `@Component` diz "isto é um bean" e o escopo vem à parte. No CDI, a própria anotação de escopo já diz as duas coisas. Se o time quiser um nome de papel na anotação, o CDI permite criar um **estereótipo** (`Servico.java` do projeto de demonstração):

```java
/** Estereótipo próprio: quem usa @Servico ganha o escopo @ApplicationScoped. */
@Stereotype
@ApplicationScoped
@Retention(RUNTIME)
@Target(TYPE)
public @interface Servico {
}
```

A `ConsultaDeChaves` da seção 3 usa `@Servico` no lugar de `@ApplicationScoped`, e o contêiner a registrou como `@ApplicationScoped` (teste `escoposDeclaradosEPadrao`).

**A extensão `quarkus-spring-di`.** Ela lê as anotações do Spring e as traduz para CDI no build. Não sobe um contexto Spring: `BeanPostProcessor` não roda, `@Conditional` é ignorado e `@Autowired(required = false)` não funciona. O mapeamento, conferido num experimento com a extensão na 3.15.7:

| Anotação Spring | Vira, no ArC |
|---|---|
| `@Component`, `@Service`, `@Repository` | `@Singleton` |
| `@Scope("singleton")` | `@Singleton` |
| `@Scope("prototype")` | `@Dependent` |
| `@Scope("request")` | `@RequestScoped` |
| `@Configuration` | `@ApplicationScoped` |
| `@Bean` (sem `@Scope`) | `@Singleton` |

Repare: os estereótipos do Spring viram `@Singleton`, sem proxy, e portanto sem `@InjectMock`. Por isso, ao migrar de vez, o projeto guiado usa `@ApplicationScoped`. A extensão serve de ponte durante uma migração, não de destino.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no mapeamento da `quarkus-spring-di` (guia da extensão, versões 3.15 e 3.40).

---

## 8. Guia rápido: qual escopo?

| Caso | Escopo |
|---|---|
| Serviço, repositório, *gateway* para outro sistema | `@ApplicationScoped` (o padrão do módulo) |
| Bean sem dependências que nunca será trocado em teste e cujo custo de chamada importa | `@Singleton` |
| Recurso REST | Nenhuma anotação (é `@Singleton`); só dependências nos campos |
| Dado da requisição usado em várias camadas (identidade, correlação, *tenant*) | `@RequestScoped` |
| Objeto auxiliar barato, com estado próprio, que não deve ser compartilhado | `@Dependent` |
| Produtor de algo caro (cliente, *pool*, conexão) | Declare `@ApplicationScoped` no `@Produces`; sem isso, é `@Dependent` |
| Estado que precisa sobreviver a reinícios ou ser visto por várias instâncias do serviço | Nenhum escopo resolve: banco |

E, em qualquer escopo compartilhado: **campo mutável só com estrutura *thread-safe***, e regra "verificar e gravar" garantida por uma operação atômica ou pelo banco.

> **Da 3.15 até a 3.40** — as mudanças desta leitura estão nos quadros das seções 2 a 7: nenhuma afeta o que foi descrito aqui, exceto a nota da 3.35 sobre `@Transactional` com `Uni`, que não se aplica ao nosso código.

---

## Referências

- Introdução ao CDI no Quarkus (bean, contêiner, escopos, *client proxy*, `@ApplicationScoped` × `@Singleton`): https://quarkus.io/version/3.15/guides/cdi
- Referência do CDI no Quarkus (descoberta, beans removidos, CDI Lite, contexto de requisição, `@Lock`, criação na subida): https://quarkus.io/version/3.15/guides/cdi-reference · versão 3.40: https://quarkus.io/guides/cdi-reference
- Testes no Quarkus (`@InjectMock`, `QuarkusMock`, `@MockitoConfig(convertScopes = true)`): https://quarkus.io/version/3.15/guides/getting-started-testing
- Quarkus REST (modelo de execução, `@Blocking`, `@NonBlocking`): https://quarkus.io/version/3.15/guides/rest
- Arquitetura reativa do Quarkus (threads de I/O e *worker threads*): https://quarkus.io/version/3.15/guides/quarkus-reactive-architecture
- Threads virtuais no Quarkus (`@RunOnVirtualThread`): https://quarkus.io/version/3.15/guides/virtual-threads
- JWT no Quarkus (escopo do `JsonWebToken`): https://quarkus.io/version/3.15/guides/security-jwt
- Compatibilidade com Spring DI: https://quarkus.io/version/3.15/guides/spring-di
- Especificação Jakarta CDI 4.1 (escopo padrão, anotações que definem bean, *client proxy*, `@Dependent`): https://jakarta.ee/specifications/cdi/4.1/jakarta-cdi-spec-4.1.html
- Especificação Jakarta REST 3.1 (ciclo de vida padrão do recurso, seção 3.1.1): https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html
- Tutorial Jakarta EE, *session beans* (*pool* de `@Stateless`, `@Singleton` do EJB): https://jakarta.ee/learn/docs/jakartaee-tutorial/current/entbeans/ejb-intro/ejb-intro.html
- Concorrência gerenciada do `@Singleton` do EJB (bloqueio de escrita por padrão), na documentação do Apache TomEE: https://tomee.apache.org/latest/docs/singleton-beans.html
- Escopos do Spring: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html · `@Lazy`: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html
- RFC 9110, métodos idempotentes (seção 9.2.2): https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2
- Cabeçalho `Idempotency-Key` (*Internet-Draft*): https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
