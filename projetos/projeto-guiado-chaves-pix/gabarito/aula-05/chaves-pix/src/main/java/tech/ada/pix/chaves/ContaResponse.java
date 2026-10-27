package tech.ada.pix.chaves;

/**
 * Resposta do serviço de contas, do ponto de vista do chaves-pix (cópia do contrato, sem dependência de código).
 */
public record ContaResponse(String agencia, String numero, String titularCpf) {
}
