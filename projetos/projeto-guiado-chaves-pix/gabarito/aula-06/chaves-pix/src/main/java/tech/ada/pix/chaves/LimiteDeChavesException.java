package tech.ada.pix.chaves;

/**
 * Aula 6: exceção de domínio; o ExceptionMappers responde 422 (regra de negócio ferida).
 */
public class LimiteDeChavesException extends RuntimeException {

    public LimiteDeChavesException(String agencia, String numeroConta, int limite) {
        super("A conta " + agencia + "/" + numeroConta + " já tem " + limite + " chaves");
    }
}
