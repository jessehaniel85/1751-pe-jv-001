package tech.ada.pix.contas;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Aula 5: o serviço que o chaves-pix consulta para confirmar que a conta existe.
 */
@Path("/contas")
@Produces(MediaType.APPLICATION_JSON)
public class ContaResource {

    @GET
    @Path("/{agencia}/{numero}")
    public ContaResponse buscar(@PathParam("agencia") String agencia, @PathParam("numero") String numero) {
        return Conta.buscar(agencia, numero)
                .map(ContaResponse::de)
                .orElseThrow(() -> new NotFoundException("Conta " + agencia + "/" + numero + " não encontrada"));
    }
}
