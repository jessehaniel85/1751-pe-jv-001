# Estado após a Aula 6: validação e tratamento de erros

**Foco:** Bean Validation com restrição própria; um formato só para todo erro.

## O que funciona
- `@ChaveValida`, restrição **de classe** sobre `NovaChaveRequest`, e `ChaveValidaValidator` (um `switch` no enum
  escolhe a regra):
  - CPF: 11 dígitos, dígitos verificadores corretos e não todos iguais; e igual ao `titularCpf` (regra do DICT: a chave CPF
    é o CPF do próprio titular), senão 400 com `"valor: a chave CPF precisa ser o CPF do próprio titular (titularCpf)"`;
  - e-mail: `nome@dominio.tld`;
  - telefone: `+55`, DDD de dois dígitos sem zero e número de 8 ou 9 dígitos (ex.: `+5561988887777`);
  - aleatória: o cliente **não** envia valor (antes era ignorado; agora é 400).
- Restrições de campo: `@NotNull tipo`, `@NotBlank agencia/numeroConta`, `titularCpf` com 11 dígitos; `@Valid` no POST e no PUT.
- `ErroResposta(status, erro, mensagens)` e `ExceptionMappers` (`@ServerExceptionMapper`): validação → 400 (uma
  mensagem por campo, `"valor: CPF inválido ..."`), `ChaveNaoEncontradaException` → 404, `ChaveDuplicadaException` → 409,
  `LimiteDeChavesException` e `ContaInexistenteException` → 422, `ContasIndisponivelException` → 503.
- `ValidationMessages.properties` com as chaves `jakarta.validation.constraints.*` (o prefixo `javax.` é ignorado sem
  aviso) e `quarkus.default-locale=pt-BR`, para a mensagem não depender da localidade da máquina.
- Extensão nova: `quarkus-hibernate-validator`.

## O que muda em relação à Aula 5
As exceções deixam de herdar de `WebApplicationException` e viram `RuntimeException` de domínio; o status passa a ser
decidido em `ExceptionMappers`. O teste da chave aleatória passa a não enviar valor.

**Ponte do Spring:** `@ServerExceptionMapper` num só lugar faz o papel do `@RestControllerAdvice` + `@ExceptionHandler`.
A restrição customizada é a mesma API (`ConstraintValidator`) do Spring, só que com `jakarta.validation`.

Armadilha encontrada ao montar o gabarito: `RestResponse.Status` (enum) não tem o 422; `Status.fromStatusCode(422)`
devolve `null` e a resposta vira 500. O mapper usa `ResponseBuilder.create(int)`.

## Testes
`mvn -B verify` (18/10/2026): **57 testes, 0 falhas**: `chaves-pix` 54 (`ValidacaoTest` 10, `ErrosUniformesTest` 5,
`ChaveValidaValidatorTest` 8 e os anteriores) + `contas` 3.

## Como rodar
```bash
mvn verify
curl -s -X POST localhost:8080/chaves -H 'Content-Type: application/json' \
  -d '{"tipo":"CPF","valor":"12345678900","agencia":"0001","numeroConta":"777777","titularCpf":"12345678909"}'
# {"status":400,"erro":"Requisição inválida","mensagens":["valor: CPF inválido (11 dígitos, com dígitos verificadores corretos)"]}
```

## Próxima aula
Qualquer pessoa cadastra e apaga chaves. Na Aula 7: quem é você? (autenticação)
