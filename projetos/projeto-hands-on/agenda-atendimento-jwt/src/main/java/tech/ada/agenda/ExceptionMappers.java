package tech.ada.agenda;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.ForbiddenException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ExceptionMappers {

  @ServerExceptionMapper
  public RestResponse<ErroResposta> validacao(ConstraintViolationException e) {
    List<String> mensagens = e.getConstraintViolations().stream()
      .map(ExceptionMappers::descrever)
      .sorted()
      .toList();
    return resposta(new ErroResposta(400, "Requisição inválida", mensagens));
  }

  @ServerExceptionMapper
  public RestResponse<ErroResposta> naoEncontrado(AgendamentoNaoEncontradoException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.NOT_FOUND.code(), "Agendamento não encontrado", e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<ErroResposta> horarioOcupado(HorarioOcupadoException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.CONFLICT.code(), "Horário ocupado", e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<ErroResposta> limite(LimiteDeAgendamentoException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.UNPROCESSABLE_ENTITY.code(), "Limite de agendamentos por cliente", e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<ErroResposta> diaSemAtendimento(DiaSemAtendimentoException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.UNPROCESSABLE_ENTITY.code(), "Dia sem atendimento", e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<ErroResposta> calendarioIndisponivel(CalendarioIndisponivelException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.SERVICE_UNAVAILABLE.code(), "Calendário indisponível", e.getMessage()));
  }

  /** Complemento JWT: login ou senha errados no POST /login. */
  @ServerExceptionMapper
  public RestResponse<ErroResposta> credencialInvalida(CredencialInvalidaException e) {
    return resposta(ErroResposta.de(HttpResponseStatus.UNAUTHORIZED.code(), "Não autenticado", e.getMessage()));
  }

  /** Aula 8: 403 com o mesmo corpo, tanto do @RolesAllowed quanto da regra de posse do service. */
  @ServerExceptionMapper
  public RestResponse<ErroResposta> acessoNegado(ForbiddenException e) {
    String mensagem = e.getMessage() == null ? "Seu papel não permite esta operação" : e.getMessage();
    return resposta(ErroResposta.de(403, "Acesso negado", mensagem));
  }

  private static RestResponse<ErroResposta> resposta(ErroResposta erro) {
    return RestResponse.ResponseBuilder.<ErroResposta>create(erro.status()).entity(erro).build();
  }

  private static String descrever(ConstraintViolation<?> violacao) {
    String caminho = violacao.getPropertyPath().toString();
    return caminho.substring(caminho.lastIndexOf('.') + 1) + ": " + violacao.getMessage();
  }

}
