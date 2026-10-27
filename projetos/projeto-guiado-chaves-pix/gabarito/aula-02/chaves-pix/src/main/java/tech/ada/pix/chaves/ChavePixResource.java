package tech.ada.pix.chaves;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

/**
 * Recurso REST das chaves. Só traduz HTTP para chamadas ao service (nível 2 de Richardson:
 * verbo certo, status certo, recurso identificado pela URI).
 */
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    @Inject
    ChavePixService service;

    @POST
    public RestResponse<ChavePixResponse> cadastrar(NovaChaveRequest request, @Context UriInfo uriInfo) {
        ChavePix chave = service.cadastrar(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(chave.getId().toString()).build();
        return RestResponse.ResponseBuilder.<ChavePixResponse>created(location).entity(ChavePixResponse.de(chave)).build();
    }

    @GET
    public List<ChavePixResponse> listar(@QueryParam("titular") String titularCpf) {
        return service.listar(titularCpf).stream().map(ChavePixResponse::de).toList();
    }

    @GET
    @Path("/{id}")
    public ChavePixResponse buscar(@PathParam("id") Long id) {
        return ChavePixResponse.de(service.buscar(id));
    }

    @PUT
    @Path("/{id}")
    public ChavePixResponse trocarConta(@PathParam("id") Long id, TrocaContaRequest request) {
        return ChavePixResponse.de(service.trocarConta(id, request));
    }

    @DELETE
    @Path("/{id}")
    public void remover(@PathParam("id") Long id) {
        service.remover(id);
    }
}
