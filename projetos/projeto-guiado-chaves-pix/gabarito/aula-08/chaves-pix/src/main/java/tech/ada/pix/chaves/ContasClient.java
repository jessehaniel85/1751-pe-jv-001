package tech.ada.pix.chaves;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Aula 5: cliente REST declarativo (como um @FeignClient / @HttpExchange do Spring).
 * A URL vem de quarkus.rest-client.contas.url, pelo configKey.
 */
@Path("/")
@RegisterRestClient(configKey = "contas")
@Produces(MediaType.APPLICATION_JSON)
public interface ContasClient {

    @GET
    @Path("contas/{agencia}/{numero}")
    ContaResponse buscar(@PathParam("agencia") String agencia, @PathParam("numero") String numero);

    @GET
    @Path("q/health/ready")
    String prontidao();

    /**
     * 404 do serviço de contas é resposta de negócio (a conta não existe), não falha técnica:
     * vira ContaInexistenteException, que o Fault Tolerance não repete nem conta no circuito.
     * Para os demais status, null mantém o comportamento padrão (WebApplicationException).
     */
    @ClientExceptionMapper
    static RuntimeException mapear(Response response) {
        return response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()
                ? new ContaInexistenteException()
                : null;
    }
}
