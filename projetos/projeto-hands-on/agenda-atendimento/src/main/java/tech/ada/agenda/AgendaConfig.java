package tech.ada.agenda;

import io.smallrye.config.ConfigMapping;
import java.time.LocalTime;

@ConfigMapping(prefix = "agenda")
public interface AgendaConfig {

  Expediente expedientes();

  int limitePorCliente();

  interface Expediente {
    LocalTime abertura();
    LocalTime encerramento();
    int duracaoMinutos();
  }

}
