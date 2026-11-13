package tech.ada.agenda;

import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.ws.rs.ClientErrorException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DiaSemAtendimentoException extends ClientErrorException {

  public DiaSemAtendimentoException(String agencia, LocalDate data, String motivo) {
    super(String.format("A agência %s não atende em %s: %s", agencia,
      DateTimeFormatter.ofPattern("dd-MM-yyyy").format(data), motivo),
      HttpResponseStatus.UNPROCESSABLE_ENTITY.code());
  }
}
