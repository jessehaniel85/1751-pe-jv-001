package tech.ada.pix.chaves;

import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Response;

/**
 * Aula 5: o serviço de contas não respondeu a tempo (ou o circuito está aberto).
 * A chave não é cadastrada sem conta confirmada: 503, e o cliente tenta de novo mais tarde.
 */
public class ContasIndisponivelException extends ServerErrorException {

    public ContasIndisponivelException() {
        super("Serviço de contas indisponível; tente novamente em instantes", Response.Status.SERVICE_UNAVAILABLE);
    }
}
