# Material do Aluno — Aula 9: Banca do projeto final

> **Quarta, 18/11/2026 · Tempo de leitura:** ~5 min. Hoje não há conteúdo novo: cada grupo apresenta o projeto final, defende as decisões e responde à arguição. **As regras valem como estão no brief do projeto final** (`projetos/projeto-final-grupo/brief-alunos.md`): rubrica, pesos, contrato de entrega, `AVALIACAO.md` e prazo. Este texto só ajuda você a se preparar; se algo aqui parecer diferente do brief, vale o brief.

---

## 1. Como a banca funciona

A ordem dos grupos foi sorteada na Aula 8. Cada grupo tem cerca de `130 min ÷ nº de grupos` (brief, seção 6), em quatro partes:

| Parte | O que acontece | O que você precisa ter pronto |
|---|---|---|
| **Demonstração** | subir os dois serviços **seguindo só o README** e mostrar o fluxo principal e **um caminho de falha** (por exemplo, o serviço chamado fora do ar e o fallback ou o erro tratado) | README com comandos e portas; usuários de teste fictícios |
| **Defesa das decisões** | não só "o que faz", mas "por que assim": a chamada entre os serviços, a política de Fault Tolerance, o corpo de erro, os papéis e as regras de acesso, o que ficou em configuração | as justificativas escritas no README |
| **Arguição individual** | **cada integrante** responde por uma parte do projeto, que não precisa ser a que escreveu | conhecer o projeto inteiro, não só a sua parte |
| **Perguntas dos colegas e uso de IA** | uma ou duas perguntas da turma; como o grupo usou IA e o que validou à mão | a seção de IA do README ou do `AVALIACAO.md` |

O que a banca avalia é o domínio do conteúdo e a qualidade das decisões, com evidência. Falha de ambiente (rede, JDK da máquina) não conta contra o grupo (brief, princípio anti-ambiente). Se a máquina de quem apresenta não subir o projeto, outro integrante apresenta da própria máquina; em último caso, o grupo mostra a suíte de testes rodando. A imagem nativa não é cobrada no projeto: foi só demonstração na Aula 5.

---

## 2. Autoavaliação pela rubrica

A rubrica tem 9 critérios com níveis de 0 a 3 (brief, seção 4). Antes da banca, releia o seu `AVALIACAO.md` com olhar de avaliador:

- [ ] Cada critério aponta para uma **evidência que existe**: arquivo, classe, teste ou commit. O que não for encontrado não conta.
- [ ] Cada critério de 1 a 6 tem pelo menos um teste, **incluindo o caminho de erro**: `400` com o corpo padrão, `401` e `403`, o fallback acionado com o serviço chamado simulado como indisponível.
- [ ] O nível que vocês se deram é sustentável pela evidência. Nível alto sem prova pesa contra na arguição.
- [ ] `mvn verify` verde, e a **contagem real** de testes anotada (no `AVALIACAO.md`, critério 7).
- [ ] Versão do Quarkus e do Java declaradas no README. Se o grupo usou uma versão mais nova que a 3.15.7, o `mvn verify` precisa estar verde nela.
- [ ] Commits ao longo das semanas, não um envio único no fim.

No fim da aula, cada pessoa também preenche um formulário de autoavaliação individual nas competências do módulo, e a turma recebe uma devolutiva coletiva pela rubrica.

---

## 3. Preparando a arguição

A arguição é individual e pode cair em qualquer parte do projeto. Treine com o grupo, cada um explicando a parte que **não** escreveu. Para cada tema, você deveria conseguir mostrar o código e o teste, e explicar o porquê:

| Tema (aula) | Pergunte-se |
|---|---|
| API REST e CDI (1–2) | por que este status HTTP aqui? por que este escopo de bean? |
| Panache e transações (3) | onde está a transação deste cadastro? o que acontece se a exceção for verificada? |
| Configuração, health e métricas (4) | que propriedade mudaria por ambiente, e qual é o nome da variável de ambiente correspondente? |
| REST Client e Fault Tolerance (5) | com o serviço chamado fora, quantas tentativas acontecem antes do fallback? o que não deve ser repetido? |
| Validação e erros (6) | o que é `400` e o que é `422` no seu domínio? onde a exceção vira HTTP? |
| Autenticação (7) | onde estão os usuários e como a senha é guardada? qual a diferença entre `401` e `403`? |
| Autorização (8) | que regra depende só do papel e qual depende do dado? onde cada uma fica? |

**Quem portou o projeto do BE-JV-010:** esteja pronto para dizer o que foi diferente do Spring e por quê (injeção, configuração por perfil, testes). Porte linha a linha, sem decisão, aparece rápido na arguição.

💭 **Pare e reflita.** Em uma frase: qual foi a decisão mais difícil do seu projeto? Essa frase é um bom começo de apresentação.

---

**Antes de entrar na banca**

- O prazo de entrega é **hoje, às 23:59 (horário de Brasília)**, pelo horário do commit (seção 3 do brief). Depois da apresentação, ainda dá para fazer retoques até esse horário.
- Rode o projeto do zero, numa pasta limpa, seguindo só o README. Se você precisou de um passo que não está escrito, escreva.
- Tenha os dois terminais (um por serviço) e os comandos de `curl` do caminho de falha prontos para colar.
