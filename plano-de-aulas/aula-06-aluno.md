# Material do Aluno — Aula 6: Validação e tratamento de erros

> **Quarta, 11/11/2026 · Tempo de leitura:** ~14 min. Ao fim da Aula 5, a API aceitava CPF com dígito verificador errado e respondia erros sem corpo nenhum (`content-length: 0`). Hoje ela passa a recusar o que está mal formado **na porta de entrada** e a responder todo erro do mesmo jeito, com uma mensagem que o cliente consegue usar para se corrigir sozinho.

---

## 1. Quem valida o quê

🎬 **Imagine o cenário.** Um banco digital tem um formulário de abertura de conta: nome, CPF, data de nascimento, CEP, e-mail e celular. Metade das reclamações do atendimento é "o app disse *erro inesperado*". O front-end valida algumas coisas, a API valida outras, e o banco de dados recusa o resto com um erro de *constraint* que vira `500`. Um cliente de 17 anos chegou até o fim do cadastro e só foi recusado na análise, dois dias depois.

O problema não é falta de validação; é validação **no lugar errado**, com resposta que não diz nada. Ajuda separar as regras em quatro tipos:

| Tipo de regra | Exemplo no projeto guiado | Onde fica | Resposta |
|---|---|---|---|
| **Formato** de um campo | `titularCpf` tem 11 dígitos | anotação no campo do DTO | `400` |
| **Coerência entre campos** | o `valor` precisa combinar com o `tipo` da chave | restrição de classe no DTO | `400` |
| **Regra que depende de estado** | a chave já existe; a conta já tem 5 chaves | service | `409` (conflito) e `422` (regra) |
| **Falha técnica** | o serviço de contas não responde | gateway + Fault Tolerance (Aula 5) | `503` |

A diferença entre `400` e `422` é útil: `400` diz "o pedido em si está errado, reenviar igual vai falhar sempre"; `422` diz "o pedido está bem formado, mas o estado atual não permite". O `409` fica para o conflito com um recurso que já existe. Sua equipe pode preferir outra convenção; o que não pode é a API ser **inconsistente**.

💭 **Pare e reflita.** Validar no front-end dispensa validar na API? Quem mais chama a sua API além da tela?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 (é desenho, não API).

---

## 2. Validação na fronteira com Jakarta Validation

A extensão `quarkus-hibernate-validator` traz a **Jakarta Validation** (o antigo Bean Validation) implementada pelo Hibernate Validator. As restrições moram no DTO. Do gabarito (`gabarito/aula-06/chaves-pix/src/main/java/tech/ada/pix/chaves/NovaChaveRequest.java`):

```java
@ChaveValida
public record NovaChaveRequest(
        @NotNull TipoChave tipo,
        String valor,
        @NotBlank String agencia,
        @NotBlank String numeroConta,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "{titular.cpf.formato}") String titularCpf) {
}
```

E quem dispara a validação é o `@Valid` no parâmetro do recurso (`ChavePixResource`):

```java
@POST
public RestResponse<ChavePixResponse> cadastrar(@Valid @NotNull NovaChaveRequest request, @Context UriInfo uriInfo) {
```

O `@Valid` faz a validação rodar **antes** do método. Se algo falhar, o método nem é chamado, e o Quarkus responde `400` sozinho. Sem nenhum tratamento nosso, a resposta padrão do Quarkus 3.15.7 é esta (saída real, `POST` sem agência e com e-mail sem domínio):

```
HTTP/1.1 400 Bad Request
content-length: 201
Content-Type: application/json;charset=UTF-8
validation-exception: true

{"title":"Constraint Violation","status":400,"violations":[{"field":"cadastrar.request.valor","message":"e-mail inválido"},{"field":"cadastrar.request.agencia","message":"não pode ficar em branco"}]}
```

(A ordem das violações não é garantida: numa segunda execução, `agencia` veio antes de `valor`.)

Funciona, mas repare em dois detalhes: o nome do campo vem com o caminho interno (`cadastrar.request.agencia`, nome do método Java e do parâmetro), e o formato é diferente do que os outros erros da API vão ter. Na seção 4 a gente uniformiza.

Uma pegadinha importante: o `400` automático vale para o **parâmetro do endpoint**. Se a mesma violação for lançada por um método do service anotado com restrições, sem tratamento ela vira `500`. Valide na fronteira.

> **Da 3.15 até a 3.40** — O Hibernate Validator sobe de 8.0 para 9.1, e a Jakarta Validation de 3.0 para 3.1 (BOMs 3.15.7 e 3.40.1). As anotações e o `@Valid` no recurso continuam iguais: sem mudança relevante no nosso código.

---

## 3. Restrição de classe: quando a regra olha dois campos

O formato do `valor` depende do `tipo`: um CPF, um e-mail, um telefone ou nada (na chave aleatória, quem gera o UUID é o servidor). Uma anotação no campo `valor` não enxerga o `tipo`. Por isso `@ChaveValida` é uma restrição **de classe**, aplicada ao record inteiro (`gabarito/aula-06/chaves-pix/src/main/java/tech/ada/pix/chaves/ChaveValida.java`):

```java
@Documented
@Constraint(validatedBy = ChaveValidaValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChaveValida {

    String message() default "{tech.ada.pix.chaves.ChaveValida.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

O validador escolhe a regra pelo tipo com um `switch` no enum e, quando recusa, **aponta a violação para o campo `valor`**, para a mensagem sair como "valor: CPF inválido" e não como um erro genérico do objeto (`ChaveValidaValidator.java`):

```java
@Override
public boolean isValid(NovaChaveRequest request, ConstraintValidatorContext context) {
    if (request == null || request.tipo() == null) {
        return true; // tipo ausente é problema do @NotNull, não desta regra
    }
    Regra regra = regraDo(request.tipo());
    if (!regra.aceita().test(request.valor())) {
        return rejeitar(context, regra.mensagem());
    }
    if (request.tipo() == TipoChave.CPF && !request.valor().equals(request.titularCpf())) {
        return rejeitar(context, "{chave.cpf.outro-titular}");
    }
    return true;
}

/** A violação vai para o campo valor, para o mapper escrever "valor: <mensagem>". */
private static boolean rejeitar(ConstraintValidatorContext context, String mensagem) {
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(mensagem)
            .addPropertyNode("valor")
            .addConstraintViolation();
    return false;
}

private static Regra regraDo(TipoChave tipo) {
    return switch (tipo) {
        case CPF -> new Regra(ChaveValidaValidator::cpfValido, "{chave.cpf.invalida}");
        case EMAIL -> new Regra(valor -> casa(EMAIL, valor), "{chave.email.invalida}");
        case TELEFONE -> new Regra(valor -> casa(TELEFONE, valor), "{chave.telefone.invalida}");
        case ALEATORIA -> new Regra(valor -> valor == null || valor.isBlank(), "{chave.aleatoria.com-valor}");
    };
}
```

O cálculo do dígito verificador do CPF está no mesmo arquivo (pesos decrescentes, módulo 11). Leia-o com calma; não precisa decorar. Um detalhe merece atenção: `111.111.111-11` **passa na conta** do dígito verificador, e mesmo assim não é CPF válido. Por isso a regra também recusa dígitos todos iguais.

**Uma regra a mais para o CPF: ele precisa ser o do próprio titular.** No diretório de chaves do Banco Central, a chave do tipo CPF é o CPF do dono da conta: ninguém registra o CPF de outra pessoa como chave. O validador confere isso depois do dígito verificador (`valor` igual a `titularCpf`). Por que aqui, e com `400`? Porque a regra se decide só com o corpo da requisição, sem consultar banco nem outro serviço, e a restrição de classe já enxerga os dois campos. Regras que dependem de estado (a conta tem vaga? a conta existe?) ficam no service e respondem `422`.

Repare no que **não** está no validador: nada de consultar o banco. "Essa chave já existe?" depende de estado e fica no service (`409`). Validador que consulta banco fica lento, acoplado e difícil de testar.

✍️ **Experimente agora.** Com os dois serviços no ar, mande pedidos inválidos e compare com as saídas reais abaixo (já com o tratamento da seção 4):

```
# CPF com dígito verificador errado
$ curl -s -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"CPF","valor":"12345678900","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
{"status":400,"erro":"Requisição inválida","mensagens":["valor: CPF inválido (11 dígitos, com dígitos verificadores corretos)"]}

# CPF válido, mas de outra pessoa (o titular é 12345678909)
$ curl -s -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"CPF","valor":"52998224725","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
{"status":400,"erro":"Requisição inválida","mensagens":["valor: a chave CPF precisa ser o CPF do próprio titular (titularCpf)"]}

# telefone sem +55
{"status":400,"erro":"Requisição inválida","mensagens":["valor: telefone inválido (formato +55, DDD e número, ex.: +5561988887777)"]}

# chave aleatória com valor
{"status":400,"erro":"Requisição inválida","mensagens":["valor: chave aleatória não recebe valor: o servidor gera o UUID"]}

# vários erros de uma vez: e-mail sem domínio, agência vazia, titularCpf com 3 dígitos
{"status":400,"erro":"Requisição inválida","mensagens":["agencia: não pode ficar em branco","titularCpf: deve ter 11 dígitos, sem pontos nem traço","valor: e-mail inválido"]}
```

Teste também `11111111111` como CPF: a resposta é a mesma do dígito errado.

💭 **Pare e reflita.** Peça ao Copilot (se você tiver acesso) um validador de CPF e teste com `11111111111`. Ele recusa? Quantos casos de teste você escreveria antes de confiar num validador gerado?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40: `@Constraint`, `ConstraintValidator` e `addPropertyNode` continuam iguais na Jakarta Validation 3.1.

---

## 4. Um corpo só para todo erro: `@ServerExceptionMapper`

Primeiro, o formato. O gabarito define um record (`gabarito/aula-06/chaves-pix/src/main/java/tech/ada/pix/chaves/ErroResposta.java`):

```java
public record ErroResposta(int status, String erro, List<String> mensagens) {

    public static ErroResposta de(int status, String erro, String mensagem) {
        return new ErroResposta(status, erro, List.of(mensagem));
    }
}
```

O status se repete no corpo de propósito: quem só lê o JSON (um log, uma fila) não vê o cabeçalho HTTP. A lista de mensagens permite, na validação, uma mensagem por campo.

Depois, a tradução de exceção para HTTP, **num lugar só**. O `ExceptionMappers` é uma classe comum com um método `@ServerExceptionMapper` por tipo de exceção; vale para a API inteira, e o Quarkus escolhe o método do tipo mais específico (trecho de `ExceptionMappers.java`):

```java
public class ExceptionMappers {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @ServerExceptionMapper
    public RestResponse<ErroResposta> validacao(ConstraintViolationException e) {
        List<String> mensagens = e.getConstraintViolations().stream()
                .map(ExceptionMappers::descrever)
                .sorted()
                .toList();
        return resposta(new ErroResposta(400, "Requisição inválida", mensagens));
    }

    @ServerExceptionMapper
    public RestResponse<ErroResposta> duplicada(ChaveDuplicadaException e) {
        return resposta(ErroResposta.de(409, "Chave duplicada", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ErroResposta> limite(LimiteDeChavesException e) {
        return resposta(ErroResposta.de(UNPROCESSABLE_ENTITY, "Limite de chaves por conta", e.getMessage()));
    }

    // ... naoEncontrada (404), contaInexistente (422), contasIndisponivel (503)

    private static RestResponse<ErroResposta> resposta(ErroResposta erro) {
        // create(int): o enum Status não tem 422, então o status vai como número.
        return RestResponse.ResponseBuilder.<ErroResposta>create(erro.status()).entity(erro).build();
    }
```

Duas decisões de desenho que valem para o seu projeto:

- **A exceção de domínio não sabe nada de HTTP.** Nas Aulas 2 a 5, `ChaveDuplicadaException` estendia `ClientErrorException` e carregava o `409`. Agora ela estende `RuntimeException`, e quem decide o status é o mapper. O service fica independente de HTTP (poderia ser chamado por um job ou por uma fila), e o mapeamento fica num lugar só.
- **O `422` vai como número.** O comentário do gabarito registra uma armadilha encontrada ao montá-lo: o enum `RestResponse.Status` não tem o 422; `Status.fromStatusCode(422)` devolve `null`, e a resposta virava `500`. Por isso `ResponseBuilder.create(int)`.

Saídas reais, com os dois serviços no ar:

```
HTTP/1.1 409 Conflict
{"status":409,"erro":"Chave duplicada","mensagens":["Chave EMAIL maria@exemplo.com.br já está cadastrada"]}

HTTP/1.1 422 Unprocessable Entity
{"status":422,"erro":"Limite de chaves por conta","mensagens":["A conta 0001/777777 já tem 5 chaves"]}

HTTP/1.1 422 Unprocessable Entity
{"status":422,"erro":"Conta inexistente","mensagens":["A conta informada não existe"]}

HTTP/1.1 404 Not Found
{"status":404,"erro":"Chave não encontrada","mensagens":["Chave 9999 não encontrada"]}

# serviço de contas derrubado
HTTP/1.1 503 Service Unavailable
{"status":503,"erro":"Serviço de contas indisponível","mensagens":["Serviço de contas indisponível; tente novamente em instantes"]}
```

Compare o `400` desta aula com o relatório padrão da seção 2: agora o campo sai como `agencia`, e não `cadastrar.request.agencia`, e o formato é o mesmo de todos os outros erros.

**E um mapper genérico para `Throwable`?** É tentador ter uma "rede de segurança" que transforma qualquer exceção em `500` com corpo bonito. Cuidado: ela pega também as `WebApplicationException` do próprio Quarkus. Fizemos o teste numa cópia do gabarito, com um `@ServerExceptionMapper` para `Throwable` que devolve `500`:

```
$ curl -i localhost:8080/naoexiste            # rota que não existe
HTTP/1.1 500 Internal Server Error
{"status":500,"erro":"Erro interno","mensagens":["Erro interno do servidor"]}

$ curl -i -X PATCH localhost:8080/chaves      # verbo que o recurso não aceita
HTTP/1.1 500 Internal Server Error
{"status":500,"erro":"Erro interno","mensagens":["Erro interno do servidor"]}
```

O `404` e o `405` viraram `500`, e o cliente perdeu a informação certa. Os mappers específicos continuaram valendo (`GET /chaves/9999` seguiu `404`), porque o Quarkus escolhe o tipo mais específico. Se quiser uma rede de segurança, registre o erro no log e devolva as `WebApplicationException` com o status delas. Prefira mappers específicos.

💭 **Pare e reflita.** Uma mensagem de erro com a pilha de exceção ou o SQL que falhou ajuda quem? E prejudica quem?

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40 no `@ServerExceptionMapper` e em `RestResponse`.

---

## 5. Mensagens em português, num arquivo só

As mensagens ficam em `src/main/resources/ValidationMessages.properties` (`gabarito/aula-06/chaves-pix/src/main/resources/ValidationMessages.properties`):

```properties
# Aula 6: mensagens de validação em português.
# As chaves das restrições padrão começam com "jakarta." (o prefixo antigo "javax." é ignorado sem aviso).
jakarta.validation.constraints.NotBlank.message=não pode ficar em branco
jakarta.validation.constraints.NotNull.message=é obrigatório

# Mensagens do domínio
tech.ada.pix.chaves.ChaveValida.message=valor incompatível com o tipo da chave
chave.cpf.invalida=CPF inválido (11 dígitos, com dígitos verificadores corretos)
chave.cpf.outro-titular=a chave CPF precisa ser o CPF do próprio titular (titularCpf)
chave.email.invalida=e-mail inválido
chave.telefone.invalida=telefone inválido (formato +55, DDD e número, ex.: +5561988887777)
chave.aleatoria.com-valor=chave aleatória não recebe valor: o servidor gera o UUID
titular.cpf.formato=deve ter 11 dígitos, sem pontos nem traço
```

Na anotação, a mensagem é referenciada entre chaves: `message = "{titular.cpf.formato}"`. E o `application.properties` fixa a localidade, para a mensagem não depender da configuração da máquina:

```properties
quarkus.default-locale=pt-BR
```

✍️ **Experimente agora.** Troque o prefixo da primeira linha para `javax.validation.constraints.NotBlank.message` e mande um `POST` sem agência. Na nossa execução, a mensagem voltou para o padrão do Hibernate Validator, **sem nenhum aviso no log**:

```
{"title":"Constraint Violation","status":400,"violations":[{"field":"cadastrar.request.agencia","message":"não deve estar em branco"}]}
```

(Esse teste foi feito numa cópia sem o mapper de validação, por isso o formato padrão.) Tutoriais antigos, do tempo do Java EE, usam `javax.`; no Jakarta, o prefixo é `jakarta.`. Volte o prefixo antes de seguir.

> **Da 3.15 até a 3.40** — sem mudança relevante até a 3.40: o arquivo e o prefixo `jakarta.validation.constraints.*` continuam os mesmos.

---

### No Spring era assim

| Você fazia no Spring Boot | No Quarkus |
|---|---|
| `spring-boot-starter-validation` | extensão `quarkus-hibernate-validator` |
| `@Valid @RequestBody` → `400` | `@Valid` no parâmetro do recurso → `400` automático |
| `MethodArgumentNotValidException` | `ConstraintViolationException` (`jakarta.validation`) |
| `@Validated` na classe para validar métodos do service | não precisa de `@Validated`, mas a violação no service vira `500` sem mapper |
| `@RestControllerAdvice` + `@ExceptionHandler(X.class)` | classe comum com métodos `@ServerExceptionMapper` |
| `@ExceptionHandler` dentro do controller | `@ServerExceptionMapper` dentro do recurso: vale só para aquele recurso |
| `ResponseEntity.status(409).body(erro)` | `RestResponse` (ou `Response`) com o status e o corpo |
| `ProblemDetail` (RFC 9457) | não há tipo pronto na nossa pilha; usamos o record `ErroResposta` |
| `ConstraintValidator` próprio | igual, com imports `jakarta.validation` |

`ResponseEntity` é do Spring e não existe no Quarkus. Se um exemplo da internet usar, ele não compila aqui.

---

**Para ir além** (guias oficiais da 3.15)

- Validação com Hibernate Validator: https://quarkus.io/version/3.15/guides/validation
- Quarkus REST, seção de mapeamento de exceções: https://quarkus.io/version/3.15/guides/rest

**Para levar ao projeto final** (critério 4 da rubrica)

- Validação na fronteira dos recursos principais, com `@Valid`; uma restrição personalizada se o domínio pedir (é um item opcional da rubrica).
- **Um** corpo de erro para a API inteira e um mapper por exceção de domínio. Exceção de domínio sem HTTP.
- Um teste por código de erro: `400` com o corpo padrão, e os demais (`404`, `409`, `422`, `503`) conforme o domínio.
- Quem porta o projeto do BE-JV-010: anote no README o que virou o seu `@RestControllerAdvice`.

> **Na próxima aula:** hoje qualquer pessoa cadastra chave em nome de qualquer CPF. Como a API sabe **quem** está chamando? E onde você guardaria a senha?
