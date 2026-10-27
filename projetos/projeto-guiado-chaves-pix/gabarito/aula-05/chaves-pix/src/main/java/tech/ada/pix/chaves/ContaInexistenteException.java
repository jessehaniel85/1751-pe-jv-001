package tech.ada.pix.chaves;

import jakarta.ws.rs.ClientErrorException;

/**
 * Aula 5: a conta informada não existe no serviço de contas. 422: o pedido está bem formado,
 * mas aponta para uma conta que não existe.
 */
public class ContaInexistenteException extends ClientErrorException {

    private static final int UNPROCESSABLE_ENTITY = 422;

    public ContaInexistenteException() {
        super("A conta informada não existe", UNPROCESSABLE_ENTITY);
    }
}
