package tech.ada.pix.contas;

/**
 * O que o serviço de contas expõe sobre uma conta.
 */
public record ContaResponse(String agencia, String numero, String titularCpf) {

    public static ContaResponse de(Conta conta) {
        return new ContaResponse(conta.agencia, conta.numero, conta.titularCpf);
    }
}
