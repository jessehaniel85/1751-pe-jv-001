package tech.ada.pix.chaves;

import io.micrometer.core.annotation.Timed;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
 * Aula 7: @Authenticated exige login em todos os endpoints (sem credencial, 401).
 */
@Authenticated
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    @Inject
    ChavePixService service;

    @POST
    @Timed(value = "pix.chaves.cadastro", description = "Tempo de resposta do cadastro de chave")
    public RestResponse<ChavePixResponse> cadastrar(@Valid @NotNull NovaChaveRequest request, @Context UriInfo uriInfo) {
        ChavePix chave = service.cadastrar(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(chave.id.toString()).build();
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
    public ChavePixResponse trocarConta(@PathParam("id") Long id, @Valid @NotNull TrocaContaRequest request) {
        return ChavePixResponse.de(service.trocarConta(id, request));
    }

    @DELETE
    @Path("/{id}")
    public void remover(@PathParam("id") Long id) {
        service.remover(id);
    }
}
