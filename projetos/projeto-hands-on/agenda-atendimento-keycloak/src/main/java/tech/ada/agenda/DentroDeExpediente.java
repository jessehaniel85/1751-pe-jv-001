package tech.ada.agenda;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DentroDeExpedienteValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DentroDeExpediente {

  String message() default "precisa ser dentro dos horários disponíveis para agendamento";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
