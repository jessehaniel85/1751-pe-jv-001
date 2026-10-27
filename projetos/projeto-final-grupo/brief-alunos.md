# Projeto Final em Grupo — Introdução ao Quarkus (PE-JV-001)

**Formato:** grupos de **3 a 4 integrantes** · **tema livre** · apresentação na **banca da Aula 9 (quarta, 18/11, das 15h às 18h)**.
**Apresentado na Aula 1 (28/10).** Desenvolvido majoritariamente **dentro do tempo de aula** (últimos minutos de cada encontro), com a banca na Aula 9.
**Pilha do módulo:** Quarkus **3.15.7** e Java **21**. Se o grupo quiser usar uma versão mais nova do Quarkus, pode, desde que **declare a versão no README** e o projeto continue rodando com `mvn verify`.

---

## 1. Princípio: o tema é livre, os critérios são a amarra

O grupo **escolhe o domínio e o produto** que quiser. O que **não** é negociável são os **critérios de avaliação** (seção 4): o projeto precisa exercitar o que o módulo ensina, de forma justificada. A API de Chaves PIX, que construímos juntos nas aulas, é só o exemplo do caminho: copiá-la não é o objetivo; o objetivo é aplicar os mesmos conceitos num problema próprio.

### Sugestão em destaque: portar para Quarkus o projeto final do BE-JV-010

Se o seu grupo já tem um projeto do módulo anterior (BE-JV-010, em Spring Boot), uma ótima escolha é **portar para Quarkus a parte síncrona dele: a API REST e os dados**. É exatamente a migração que os times estão fazendo no dia a dia.

- **Entra no porte:** recursos REST, persistência (Spring Data para Panache), validação, tratamento de erros, segurança.
- **Fica de fora:** filas, tópicos, cache e contract test. Esses assuntos não fazem parte deste módulo; não é preciso portá-los.
- **Atenção ao critério dos dois serviços:** se no projeto original os serviços conversavam por fila, o porte precisa de **pelo menos uma chamada síncrona entre dois serviços** (REST Client). Troque uma ligação por uma consulta síncrona e registre no README o que mudou e por quê.
- **Porte não é tradução linha a linha.** Anote no README o que foi diferente do Spring (injeção, configuração, testes). Isso ajuda muito na arguição.

### Outras sugestões de tema (escolher uma, o porte acima ou propor a sua)

| Tema | Serviço A (Panache) | Serviço B (consumido por A) | Onde o Fault Tolerance aparece | Papéis sugeridos |
|---|---|---|---|---|
| **Proposta de crédito pessoal** | `propostas`: cadastro e acompanhamento da proposta | `score`: devolve a faixa de risco de um cliente fictício | score fora do ar: a proposta fica "pendente de análise" ou é recusada com erro claro (decisão do grupo, justificada) | cliente, analista, gerente |
| **Fatura de cartão** | `faturas`: lançamentos e fechamento | `limites`: confirma limite disponível antes de aceitar um lançamento | limite indisponível: lançamento recusado com `503` ou aceito em estado pendente | titular, atendente |
| **Carteira de investimentos** | `carteira`: aplicações do investidor | `cotacoes`: preço atual do ativo | cotação indisponível: usa a última cotação gravada e sinaliza que está defasada | investidor, assessor |

Qualquer domínio do dia a dia do grupo que justifique os critérios também vale, e é **preferível**, porque aproxima do trabalho real. Evite o domínio de chaves PIX: foi o exemplo da aula.

---

## 2. Escopo escalável

O esforço acompanha o tamanho do grupo. **O CORE é obrigatório**; cada integrante acima de 3 assume **1 item opcional**.

**CORE (todo grupo entrega):**
- **Pelo menos 2 serviços Quarkus**, um **consumindo o outro por REST Client**, com **Fault Tolerance** nessa chamada (timeout, retry, circuit breaker e/ou fallback, conforme o caso, justificado).
- **Persistência com Hibernate ORM com Panache** (H2 em memória).
- **Validação de entrada e tratamento de erros padronizado**: um corpo de erro único para todos os erros da API, com códigos HTTP coerentes.
- **Autenticação com usuários em banco** (`quarkus-security-jpa`, senha em BCrypt) e **autorização (RBAC) com pelo menos 2 papéis**, com regras de acesso diferentes por papel.
- **Uso crítico de IA documentado** (critério 8): como o grupo usou IA e o que conferiu à mão.
- **Health checks e métricas** (`/q/health` e `/q/metrics`).
- **Testes `@QuarkusTest` que provam cada critério de 1 a 6, incluindo os caminhos de erro** (ver seção 4; os critérios 8 e 9 se provam pelo README e pela execução).
- **README** e **AVALIACAO.md** conforme a seção 3.
- **Roda sem Docker**: dois serviços locais (portas 8080 e 8081), H2 em memória, `mvn verify` verde.

**Opcionais (+1 por integrante acima de 3):**
- **Terceiro serviço** ou segunda integração entre serviços, também com Fault Tolerance.
- **OpenAPI** bem documentada (descrições e exemplos) com Swagger UI.
- **Métrica de negócio própria** (contador ou tempo) e **health check próprio** além dos automáticos.
- **Validador customizado** (por exemplo, uma regra que valida o objeto inteiro).
- **Registros de decisão (ADRs)** em `docs/adr/`: uma página por decisão importante (contexto, opções, escolha e consequência).

**Grupos:** de 3 a 4 integrantes (`nº de grupos = teto(N/4)`). Quem quiser pode **manter o grupo do BE-JV-010**. Distribuímos as pessoas com mais bagagem entre os grupos, uma por grupo. Os grupos precisam estar **fechados até o fim da Aula 1**. Papéis sugeridos, rotativos: arquiteto · dev das APIs e dados · dev de integração e resiliência · dev de testes e segurança.

---

## 3. Contrato de entrega (estrutura padronizada do repositório)

**Obrigatória.** Repositório do grupo no **GitHub**, com acesso de leitura para o docente, com:

```
<projeto-do-grupo>/
├── README.md                 # tema, problema, versões, arquitetura, como rodar e testar
├── AVALIACAO.md              # auto-avaliação: cada critério -> evidência (arquivo, teste ou commit). Ver seção 5
├── docs/
│   └── arquitetura.md        # os serviços, quem chama quem, portas, papéis
├── <servico-1>/              # módulo Maven Quarkus (porta 8080)
├── <servico-2>/              # módulo Maven Quarkus (porta 8081)
└── pom.xml                   # agregador (multi-módulo)
```

**O README deve ter:** tema e problema em poucas linhas · **versão do Quarkus e do Java** (3.15.7 e 21, ou a versão mais nova que o grupo escolheu) · desenho dos serviços e da chamada entre eles · usuários e papéis de teste (fictícios) · **como subir os serviços** (comandos e portas) · **como rodar os testes** (`mvn verify`).

**Regras que tornam a entrega avaliável:**
- `AVALIACAO.md` é **obrigatório** e liga **cada critério a uma evidência** (caminho de arquivo, classe, teste ou commit). O que não for encontrado não conta.
- **Commits ao longo das semanas**, não um único envio no fim. É evidência de processo e de quem fez o quê.
- **Prazo:** a entrega vale pelo horário do **commit** (committer-date em UTC) do último commit considerado. Prazo final: **quarta, 18/11, às 23:59 (horário de Brasília)**, no dia da banca. Depois da apresentação, o grupo ainda pode fazer retoques até esse horário.
- **Sem penalização por restrição de ambiente.** Se o build falhar por problema de rede ou de JDK da máquina, isso não conta contra você; só erro real de código conta.

---

## 4. Critérios de avaliação (rubrica)

Os pesos somam 100. Cada critério tem 4 níveis: **0 Insuficiente · 1 Básico · 2 Proficiente · 3 Avançado**. Nota do critério = `(nível/3) × peso`. A rubrica cobre as nove competências do módulo (mapa abaixo). A coluna **Evidência esperada** diz onde o grupo deve apontar no `AVALIACAO.md`; a coluna **Teste que prova** diz o que um `@QuarkusTest` precisa demonstrar, **inclusive o caminho de erro**.

| # | Critério | Peso | Evidência esperada | Teste que prova (incluindo o erro) |
|---|---|:---:|---|---|
| 1 | **Serviços e API REST** (recursos no nível 2 de maturidade, códigos HTTP corretos, projeto Maven multi-módulo, CDI) | 12 | módulos por serviço; recursos com verbos e status coerentes; OpenAPI | um teste por endpoint com o status esperado (201 com `Location`, 404, 409 etc.) |
| 2 | **Integração entre serviços e tolerância a falhas** (REST Client, timeout, retry, circuit breaker, fallback) | 15 | interface do cliente; anotações de Fault Tolerance; configuração por propriedades | serviço consumido simulado como indisponível (mock do cliente) e o **fallback ou o erro tratado** acionado |
| 3 | **Persistência com Panache** (entidade, repositório ou Active Record, consultas, transações) | 10 | entidades; consultas além do CRUD básico; `@Transactional` | a regra persiste e a consulta devolve o esperado |
| 4 | **Validação e tratamento de erros** (Bean Validation, exceções de domínio, corpo de erro padronizado) | 12 | anotações de validação; mapeadores de exceção; modelo do corpo de erro | entrada inválida devolve `400` com o corpo padrão; exceção de domínio devolve o código e o corpo previstos |
| 5 | **Segurança: autenticação e autorização (RBAC)** (≥ 2 papéis, regras por papel) | 15 | configuração de usuários; anotações `@RolesAllowed`/`@PermitAll`/`@DenyAll` ou regras por configuração | sem credencial: `401`; papel errado: `403`; papel certo: sucesso |
| 6 | **Config, health e métricas** (propriedades e perfis, `/q/health`, `/q/metrics`) | 13 | `application.properties` com perfis; health; métricas | `/q/health/live` e `/q/health/ready` respondem `UP`; uma métrica do projeto aparece em `/q/metrics` |
| 7 | **Testes** (um `@QuarkusTest` por critério de 1 a 6, caminhos de erro, `mvn verify` verde) | 13 | pasta `src/test`; `AVALIACAO.md` ligando critério a teste | suíte verde, com contagem real de testes, e cada critério de 1 a 6 com pelo menos um teste |
| 8 | **Uso crítico de IA** (como usaram IA; o que validaram à mão) | 5 | seção no README ou no `AVALIACAO.md` | não se aplica |
| 9 | **Execução comprovada** (sobe os dois serviços; instruções reproduzíveis) | 5 | README "como rodar"; versões declaradas | os dois serviços sobem nas portas combinadas seguindo só o README |

**Mapa das nove competências do módulo para os critérios:**

| Competência do módulo | Critério |
|---|---|
| Compreender o protocolo HTTP (requisição/resposta, verbos, códigos de status) | 1 |
| Compreender, criar e manter uma API REST no nível 2 do modelo de Richardson | 1 |
| Compreender, criar e manter projetos com Maven (dependências e bibliotecas) | 1 e 9 |
| Aplicar os conceitos fundamentais do Quarkus (imagem nativa, MicroProfile, CDI) | 2 e 6 (CDI no 1; imagem nativa não é cobrada no projeto) |
| Criar e manter projetos de API REST com Quarkus | 1 |
| Criar e manter projetos com Hibernate e Panache | 3 |
| Aplicar validação de requisições e tratamento de erros | 4 |
| Aplicar segurança com autenticação em memória e em banco de dados | 5 |
| Aplicar segurança com autorização com RBAC e JSR-250 | 5 |

O critério 7 (testes) atravessa todas as competências.

### 4.1 Descritores dos níveis

O nível de cada critério é o **mais alto cuja descrição o projeto cumpre por inteiro**, com a evidência apontada no `AVALIACAO.md`. Cada nível inclui o anterior.

| # | 0 · Insuficiente | 1 · Básico | 2 · Proficiente | 3 · Avançado |
|---|---|---|---|---|
| 1 | Um serviço só, ou recursos sem semântica HTTP (tudo `POST` e `200`) | Os dois módulos sobem e o CRUD funciona, mas há status incoerentes (`200` na criação, `500` para recurso inexistente) ou endpoints sem teste | Verbos e status corretos (`201` com `Location`, `204`, `404`, `409`/`422`), projeto multi-módulo, injeção por CDI; cada endpoint com teste do status | Além do 2: o OpenAPI descreve o que a API de fato responde, e o README justifica a modelagem dos recursos |
| 2 | Sem chamada entre serviços, ou chamada sem nenhuma tolerância a falhas | REST Client funciona no caminho feliz; anotações de Fault Tolerance sem teste da falha ou sem justificativa dos valores | URL do serviço consumido em propriedade; pelo menos timeout e fallback (ou erro tratado com código definido); teste com o cliente simulado indisponível prova o comportamento | Além do 2: combinação justificada (retry só em operação idempotente, circuit breaker com parâmetros explicados), parâmetros na configuração, e o README diz o que o usuário vê quando o serviço consumido cai |
| 3 | Sem persistência, ou dados guardados só em memória (um `Map`) | Entidades e CRUD com Panache, sem consultas além do básico ou sem `@Transactional` nas escritas | Consultas além do CRUD (filtro, contagem ou ordenação), `@Transactional` nas escritas, dados de exemplo carregados só em dev e teste; testes provam a gravação e a consulta | Além do 2: escolha justificada entre Active Record e Repository e da estratégia de geração de id |
| 4 | Entrada inválida chega ao banco ou responde `500` | Bean Validation nas entradas, mas o corpo de erro muda de um endpoint para outro, ou exceções de domínio viram `500` | Um corpo de erro único para validação e exceções de domínio, com códigos coerentes; testes do `400` e de pelo menos uma exceção de domínio | Além do 2: nenhuma resposta de erro expõe detalhe interno (stack trace, SQL), e há teste para cada código de erro do contrato |
| 5 | Endpoints abertos, ou autenticação sem papéis | Autenticação funciona, mas só um papel tem efeito, os usuários estão só em arquivo, ou faltam os testes de `401`/`403` | Usuários em banco com senha em BCrypt, senhas de teste fictícias; ≥ 2 papéis com regras diferentes; testes de `401`, `403` e sucesso | Além do 2: uma regra além do papel (por exemplo, cada cliente só vê os próprios dados), justificada e com teste |
| 6 | Valores de negócio fixos no código; sem health nem métricas | Health e métricas automáticos presentes; configuração sem perfis, ou parâmetros de negócio fixos no código | Parâmetros de negócio e URLs em propriedades, com perfis; `/q/health/live` e `/q/health/ready` testados; uma métrica do projeto aparece em `/q/metrics`, com teste | Além do 2: o perfil de produção não carrega dados de exemplo, e o README mostra como mudar um parâmetro sem recompilar (variável de ambiente) |
| 7 | Sem testes, ou `mvn verify` vermelho | Testes só do caminho feliz, ou critérios sem teste | `mvn verify` verde com a contagem de testes declarada no `AVALIACAO.md`; cada critério de 1 a 6 com pelo menos um teste, incluindo um caminho de erro | Além do 2: testes independentes da ordem e de porta fixa, sem depender do serviço real (cliente simulado), com nomes que descrevem a regra |
| 8 | Seção ausente | Lista as ferramentas usadas, sem dizer o que foi aceito, rejeitado ou conferido | Exemplos concretos: o que a IA produziu, o que o grupo conferiu e como (teste, documentação), e pelo menos um erro da IA detectado e corrigido | Além do 2: diz quando a IA atrapalhou (por exemplo, sugeriu código do Spring ou de outra versão do Quarkus) e a regra que o grupo adotou por causa disso |
| 9 | Não sobe seguindo o README | Sobe só com ajustes que o README não documenta (comando, porta, propriedade) | Os dois serviços sobem nas portas 8080 e 8081 seguindo só o README; versões declaradas | Além do 2: um roteiro de demonstração (comandos `curl` ou coleção) percorre o fluxo principal e um caminho de erro |

**Atitudinais e banca (à parte):** clareza na defesa, resposta à arguição, colaboração no grupo, avaliação entre pares.

> **Princípio anti-ambiente:** os critérios avaliam o **domínio do conteúdo e a qualidade das decisões**, não a infraestrutura disponível na sua máquina.

---

## 5. `AVALIACAO.md` — modelo que o grupo preenche

```markdown
# Auto-avaliação — <nome do projeto>
Grupo: <integrantes e papéis>
Tema/domínio: <descrição em 2 linhas>
Versões: Quarkus <x.y.z> · Java <n>

## Evidências por critério
1. Serviços e API REST — nível auto-atribuído: X
   Evidência: <módulos, recursos, testes>
2. Integração entre serviços e tolerância a falhas — X
   Evidência: <cliente, anotações, teste do fallback ou do erro tratado>
3. Persistência com Panache — X
   Evidência: <entidades, consultas, teste>
4. Validação e tratamento de erros — X
   Evidência: <validações, mapeadores, corpo de erro, teste do 400 e dos demais erros>
5. Segurança — X
   Evidência: <usuários, papéis, regras, testes 401/403>
6. Config, health e métricas — X
   Evidência: <propriedades, health, métricas, testes>
7. Testes — X
   Evidência: <classes de teste, contagem de testes do `mvn verify`>
8. Uso crítico de IA — X
   Como usamos IA e o que validamos manualmente: <texto honesto>
9. Execução — X
   Como rodar: <comandos e portas>

## Opcionais entregues
<lista, se o grupo tem mais de 3 pessoas>
```

---

## 6. Banca (Aula 9 · quarta, 18/11, 15h às 18h)

Cada grupo apresenta: **demonstração** (subir os dois serviços e mostrar o fluxo) + **defesa das decisões** (não só "o que faz", mas "por que assim") + **arguição individual** (cada integrante responde por uma parte do projeto, não só o colega que a escreveu) + perguntas dos colegas + reflexão sobre o uso de IA. O tempo por grupo é cerca de `130 min ÷ nº de grupos` (o resto da tarde é intervalo e devolutiva). A devolutiva por rubrica acontece na mesma aula.

## 7. Cronograma de maturação (dentro das aulas)

Em cada encontro, os últimos minutos são de trabalho em grupo, aplicando ao projeto o que a aula ensinou.

| Aula | Data | O grupo avança |
|---|---|---|
| 1 | Qua 28/10 | Fechar o grupo (3–4) e escolher o tema; esboçar os dois serviços e quem chama quem |
| 2 | Sex 30/10 | Contrato REST dos dois serviços (recursos e códigos HTTP); gerar os módulos pelo plugin |
| 3 | Qua 04/11 | Entidades e consultas com Panache |
| 4 | Sex 06/11 | Configuração por perfil, health checks e métricas |
| 5 | Seg 09/11 | A chamada entre os serviços com REST Client e Fault Tolerance (marco: integração rodando) |
| 6 | Qua 11/11 | Validação e corpo de erro padronizado |
| 7 | Sex 13/11 | Autenticação |
| 8 | Seg 16/11 | Autorização (papéis) e **última janela antes da banca**: fechar os testes dos caminhos de erro e o `AVALIACAO.md` |
| 9 | Qua 18/11 | Banca; retoques até as 23:59 |

Não há aula em 02/11.
