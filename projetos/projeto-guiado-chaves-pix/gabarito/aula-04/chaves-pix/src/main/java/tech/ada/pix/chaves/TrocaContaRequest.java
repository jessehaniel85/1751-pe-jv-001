package tech.ada.pix.chaves;

/**
 * Corpo do PUT /chaves/{id}: a nova conta à qual a chave passa a apontar.
 */
public record TrocaContaRequest(String agencia, String numeroConta) {
}
