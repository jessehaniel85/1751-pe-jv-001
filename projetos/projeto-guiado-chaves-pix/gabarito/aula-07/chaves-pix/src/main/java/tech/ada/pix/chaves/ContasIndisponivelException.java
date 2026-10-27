package tech.ada.pix.chaves;

/**
 * Aula 6: exceção de domínio; o ExceptionMappers responde 503. A chave não é cadastrada sem conta confirmada.
 */
public class ContasIndisponivelException extends RuntimeException {

    public ContasIndisponivelException() {
        super("Serviço de contas indisponível; tente novamente em instantes");
    }
}
