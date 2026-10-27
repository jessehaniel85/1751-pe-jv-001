package tech.ada.pix.chaves;

/**
 * Aula 6: exceção de domínio; o ExceptionMappers responde 422 (a conta informada não existe).
 */
public class ContaInexistenteException extends RuntimeException {

    public ContaInexistenteException() {
        super("A conta informada não existe");
    }
}
