package tech.ada.agenda;

import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.ws.rs.ClientErrorException;

/**
 * Aula 4: 422 (Unprocessable Entity): o pedido está bem formado, mas fere uma regra de negócio.
 */
public class LimiteDeAgendamentoException extends ClientErrorException {

  public LimiteDeAgendamentoException(String clienteCpf, int limitePorCliente) {
    super(String.format("O cliente %s já tem %d agendamentos", clienteCpf, limitePorCliente),
      HttpResponseStatus.UNPROCESSABLE_ENTITY.code() );
  }
}
