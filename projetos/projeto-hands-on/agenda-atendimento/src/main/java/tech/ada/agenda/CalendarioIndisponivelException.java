package tech.ada.agenda;

import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.ws.rs.ServerErrorException;

public class CalendarioIndisponivelException extends ServerErrorException {

  public CalendarioIndisponivelException() {
    super("Calendário indisponível: tente mais tarde",
      HttpResponseStatus.SERVICE_UNAVAILABLE.code());
  }
}
