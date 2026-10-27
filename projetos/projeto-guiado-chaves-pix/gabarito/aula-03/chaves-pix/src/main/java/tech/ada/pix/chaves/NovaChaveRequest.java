package tech.ada.pix.chaves;

/**
 * Corpo do POST /chaves. Para chave ALEATORIA o cliente não envia valor: o servidor gera um UUID.
 */
public record NovaChaveRequest(
        TipoChave tipo,
        String valor,
        String agencia,
        String numeroConta,
        String titularCpf) {
}
