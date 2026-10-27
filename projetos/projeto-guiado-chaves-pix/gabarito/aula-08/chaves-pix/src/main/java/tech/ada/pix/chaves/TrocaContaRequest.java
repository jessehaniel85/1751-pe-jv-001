package tech.ada.pix.chaves;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo do PUT /chaves/{id}: a nova conta à qual a chave passa a apontar.
 */
public record TrocaContaRequest(@NotBlank String agencia, @NotBlank String numeroConta) {
}
