package tech.ada.agenda;

import io.smallrye.config.ConfigMapping;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@ConfigMapping(prefix = "agenda")
public interface AgendaConfig {

  Expediente expediente();

  int limitePorCliente();

  interface Expediente {
    LocalTime abertura();
    LocalTime encerramento();
    int duracaoMinutos();

    default List<LocalTime> horarios() {
      List<LocalTime> horarios = new ArrayList<>();
      for (LocalTime hr = abertura(); hr.isBefore(encerramento()); hr = hr.plusMinutes(duracaoMinutos())) {
        horarios.add(hr);
      }
      return horarios;
    }
  }

}
