package tech.ada.pix.chaves;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo do POST /chaves. Para chave ALEATORIA o cliente não envia valor: o servidor gera um UUID.
 * Aula 6: restrições de campo (formato) e @ChaveValida (valor coerente com o tipo).
 */
@ChaveValida
public record NovaChaveRequest(
        @NotNull TipoChave tipo,
        String valor,
        @NotBlank String agencia,
        @NotBlank String numeroConta,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "{titular.cpf.formato}") String titularCpf) {
}
