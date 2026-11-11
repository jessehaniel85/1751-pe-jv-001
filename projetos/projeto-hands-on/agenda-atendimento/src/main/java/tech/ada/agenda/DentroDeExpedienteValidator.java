package tech.ada.agenda;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalTime;

@ApplicationScoped
public class DentroDeExpedienteValidator implements ConstraintValidator<DentroDeExpediente, LocalTime> {

  private final AgendaConfig agendaConfig;

  public DentroDeExpedienteValidator(AgendaConfig agendaConfig) {
    this.agendaConfig = agendaConfig;
  }

  @Override
  public boolean isValid(LocalTime horario, ConstraintValidatorContext context) {
    return horario == null || agendaConfig.expediente().horarios().contains(horario);
  }
}
