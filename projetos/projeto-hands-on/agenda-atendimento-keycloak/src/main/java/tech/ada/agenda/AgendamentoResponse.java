package tech.ada.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

public record AgendamentoResponse(
  Long id,
  String agencia,
  LocalDate data,
  LocalTime horario,
  String clienteCpf
) {

  public AgendamentoResponse(Agendamento agendamento) {
    this(agendamento.getId(), agendamento.getAgencia(), agendamento.getData(), agendamento.getHorario(), agendamento.getClienteCpf());
  }

}
