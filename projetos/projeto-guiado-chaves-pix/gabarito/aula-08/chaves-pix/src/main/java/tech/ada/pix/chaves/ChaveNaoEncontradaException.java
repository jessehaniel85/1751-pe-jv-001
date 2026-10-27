package tech.ada.pix.chaves;

/**
 * Aula 6: exceção de domínio; o ExceptionMappers responde 404.
 */
public class ChaveNaoEncontradaException extends RuntimeException {

    public ChaveNaoEncontradaException(Long id) {
        super("Chave " + id + " não encontrada");
    }
}
