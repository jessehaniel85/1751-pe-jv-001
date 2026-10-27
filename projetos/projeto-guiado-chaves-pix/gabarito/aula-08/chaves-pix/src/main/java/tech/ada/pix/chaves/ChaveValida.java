package tech.ada.pix.chaves;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aula 6: restrição de classe. O formato do valor depende do tipo, então a regra precisa
 * enxergar os dois campos ao mesmo tempo; uma anotação de campo não enxerga.
 */
@Documented
@Constraint(validatedBy = ChaveValidaValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChaveValida {

    String message() default "{tech.ada.pix.chaves.ChaveValida.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
