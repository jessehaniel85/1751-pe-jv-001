package tech.ada.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

public record RemarcacaoRequest(
  LocalDate data,
  LocalTime horario) {

}
