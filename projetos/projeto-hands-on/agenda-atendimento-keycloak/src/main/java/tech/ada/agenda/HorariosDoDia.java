package tech.ada.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record HorariosDoDia(
  String agencia,
  LocalDate data,
  List<LocalTime> horarios
) {

}
