package tech.ada.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

public record NovoAgendamentoRequest(
  String agencia,
  LocalDate data,
  LocalTime horario,
  String clienteCpf
) {

}
