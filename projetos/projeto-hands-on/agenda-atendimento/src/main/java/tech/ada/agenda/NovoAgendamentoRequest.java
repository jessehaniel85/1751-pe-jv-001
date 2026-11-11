package tech.ada.agenda;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.validator.constraints.br.CPF;

public record NovoAgendamentoRequest(
  @NotBlank String agencia,
  @NotNull @Future LocalDate data,
  @NotNull @DentroDeExpediente LocalTime horario,
  @NotNull @CPF @Pattern(regexp = "\\d{11}", message = "deve ter 11 dígitos, sem ponto nem traço") String clienteCpf
) {

}
