package tech.ada.pix.chaves;

import jakarta.ws.rs.ClientErrorException;

/**
 * Aula 2: 422 (Unprocessable Entity): o pedido está bem formado, mas fere uma regra de negócio.
 */
public class LimiteDeChavesException extends ClientErrorException {

    private static final int UNPROCESSABLE_ENTITY = 422;

    public LimiteDeChavesException(String agencia, String numeroConta, int limite) {
        super("A conta " + agencia + "/" + numeroConta + " já tem " + limite + " chaves", UNPROCESSABLE_ENTITY);
    }
}
