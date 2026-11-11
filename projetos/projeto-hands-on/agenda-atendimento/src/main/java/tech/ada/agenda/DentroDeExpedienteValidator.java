package tech.ada.agenda;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Provider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalTime;

@ApplicationScoped
public class DentroDeExpedienteValidator implements ConstraintValidator<DentroDeExpediente, LocalTime> {

  private final Provider<AgendaConfig> agendaConfig;

  public DentroDeExpedienteValidator(Provider<AgendaConfig> agendaConfig) {
    this.agendaConfig = agendaConfig;
  }

  @Override
  public boolean isValid(LocalTime horario, ConstraintValidatorContext context) {
    return horario == null || agendaConfig.get().expediente().horarios().contains(horario);
  }
}
