package tech.ada.pix.chaves;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

/**
 * Aula 6: tradução de exceção para HTTP num lugar só (equivale ao @RestControllerAdvice do Spring).
 * Cada método trata um tipo; o Quarkus escolhe o mais específico.
 */
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
    public RestResponse<ErroResposta> naoEncontrada(ChaveNaoEncontradaException e) {
        return resposta(ErroResposta.de(404, "Chave não encontrada", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ErroResposta> duplicada(ChaveDuplicadaException e) {
        return resposta(ErroResposta.de(409, "Chave duplicada", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ErroResposta> limite(LimiteDeChavesException e) {
        return resposta(ErroResposta.de(UNPROCESSABLE_ENTITY, "Limite de chaves por conta", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ErroResposta> contaInexistente(ContaInexistenteException e) {
        return resposta(ErroResposta.de(UNPROCESSABLE_ENTITY, "Conta inexistente", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ErroResposta> contasIndisponivel(ContasIndisponivelException e) {
        return resposta(ErroResposta.de(503, "Serviço de contas indisponível", e.getMessage()));
    }

    private static RestResponse<ErroResposta> resposta(ErroResposta erro) {
        // create(int): o enum Status não tem 422, então o status vai como número.
        return RestResponse.ResponseBuilder.<ErroResposta>create(erro.status()).entity(erro).build();
    }

    /** "cadastrar.request.valor" vira "valor: <mensagem>". */
    private static String descrever(ConstraintViolation<?> violacao) {
        String caminho = violacao.getPropertyPath().toString();
        return caminho.substring(caminho.lastIndexOf('.') + 1) + ": " + violacao.getMessage();
    }
}
