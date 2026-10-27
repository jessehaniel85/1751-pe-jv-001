package tech.ada.pix.chaves;

/**
 * Aula 6: exceção de domínio, sem nada de HTTP. Quem decide o status (409) é o ExceptionMappers.
 */
public class ChaveDuplicadaException extends RuntimeException {

    public ChaveDuplicadaException(TipoChave tipo, String valor) {
        super("Chave " + tipo + " " + valor + " já está cadastrada");
    }
}
