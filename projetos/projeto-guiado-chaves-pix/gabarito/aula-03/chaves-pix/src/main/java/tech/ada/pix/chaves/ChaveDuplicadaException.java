package tech.ada.pix.chaves;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

/**
 * Aula 2: a exceção já carrega o status HTTP (409), como um @ResponseStatus do Spring.
 * Na Aula 6 ela vira exceção de domínio e um mapper decide o status e o corpo.
 */
public class ChaveDuplicadaException extends ClientErrorException {

    public ChaveDuplicadaException(TipoChave tipo, String valor) {
        super("Chave " + tipo + " " + valor + " já está cadastrada", Response.Status.CONFLICT);
    }
}
