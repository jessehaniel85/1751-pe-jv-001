package tech.ada.pix.chaves;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Aula 6: valida o valor da chave conforme o tipo.
 * CPF: 11 dígitos com dígitos verificadores corretos e igual ao CPF do titular (regra do DICT: a chave CPF
 * é o CPF do próprio dono da conta). E-mail: formato nome@dominio.tld.
 * Telefone: E.164 do Brasil, +55, DDD de dois dígitos sem zero e número de 8 ou 9 dígitos.
 * Aleatória: o cliente não envia valor (o servidor gera o UUID).
 */
public class ChaveValidaValidator implements ConstraintValidator<ChaveValida, NovaChaveRequest> {

    private static final Pattern CPF = Pattern.compile("\\d{11}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern TELEFONE = Pattern.compile("\\+55[1-9]{2}\\d{8,9}");

    private record Regra(Predicate<String> aceita, String mensagem) {
    }

    @Override
    public boolean isValid(NovaChaveRequest request, ConstraintValidatorContext context) {
        if (request == null || request.tipo() == null) {
            return true; // tipo ausente é problema do @NotNull, não desta regra
        }
        Regra regra = regraDo(request.tipo());
        if (!regra.aceita().test(request.valor())) {
            return rejeitar(context, regra.mensagem());
        }
        if (request.tipo() == TipoChave.CPF && !request.valor().equals(request.titularCpf())) {
            return rejeitar(context, "{chave.cpf.outro-titular}");
        }
        return true;
    }

    /** A violação vai para o campo valor, para o mapper escrever "valor: <mensagem>". */
    private static boolean rejeitar(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode("valor")
                .addConstraintViolation();
        return false;
    }

    private static Regra regraDo(TipoChave tipo) {
        return switch (tipo) {
            case CPF -> new Regra(ChaveValidaValidator::cpfValido, "{chave.cpf.invalida}");
            case EMAIL -> new Regra(valor -> casa(EMAIL, valor), "{chave.email.invalida}");
            case TELEFONE -> new Regra(valor -> casa(TELEFONE, valor), "{chave.telefone.invalida}");
            case ALEATORIA -> new Regra(valor -> valor == null || valor.isBlank(), "{chave.aleatoria.com-valor}");
        };
    }

    private static boolean casa(Pattern padrao, String valor) {
        return valor != null && padrao.matcher(valor).matches();
    }

    /** Dígitos repetidos (111.111.111-11) passam na conta, mas não são CPF válido. */
    static boolean cpfValido(String cpf) {
        return casa(CPF, cpf)
                && cpf.chars().distinct().count() > 1
                && digitoVerificador(cpf, 9) == cpf.charAt(9) - '0'
                && digitoVerificador(cpf, 10) == cpf.charAt(10) - '0';
    }

    /** Soma ponderada dos primeiros dígitos (pesos decrescentes a partir de tamanho + 1), módulo 11. */
    private static int digitoVerificador(String cpf, int tamanho) {
        int soma = IntStream.range(0, tamanho)
                .map(i -> (cpf.charAt(i) - '0') * (tamanho + 1 - i))
                .sum();
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }
}
