package tech.ada.pix.chaves;

import io.micrometer.core.annotation.Timed;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
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

import static tech.ada.pix.chaves.Papeis.ATENDENTE;
import static tech.ada.pix.chaves.Papeis.AUDITOR;
import static tech.ada.pix.chaves.Papeis.TITULAR;

/**
 * Recurso REST das chaves. Só traduz HTTP para chamadas ao service (nível 2 de Richardson).
 * Aula 8: @DenyAll na classe nega por padrão; cada método abre só para os papéis certos (JSR-250).
 * Quem pode o quê fica aqui; "só as próprias chaves" depende do dado e fica no service.
 */
@DenyAll
@Path("/chaves")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChavePixResource {

    @Inject
    ChavePixService service;

    @POST
    @RolesAllowed(TITULAR)
    @Timed(value = "pix.chaves.cadastro", description = "Tempo de resposta do cadastro de chave")
    public RestResponse<ChavePixResponse> cadastrar(@Valid @NotNull NovaChaveRequest request, @Context UriInfo uriInfo) {
        ChavePix chave = service.cadastrar(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(chave.id.toString()).build();
        return RestResponse.ResponseBuilder.<ChavePixResponse>created(location).entity(ChavePixResponse.de(chave)).build();
    }

    @GET
    @RolesAllowed({TITULAR, ATENDENTE, AUDITOR})
    public List<ChavePixResponse> listar(@QueryParam("titular") String titularCpf) {
        return service.listar(titularCpf).stream().map(ChavePixResponse::de).toList();
    }

    @GET
    @Path("/tipos")
    @PermitAll
    public List<TipoChave> tipos() {
        return List.of(TipoChave.values());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({TITULAR, ATENDENTE, AUDITOR})
    public ChavePixResponse buscar(@PathParam("id") Long id) {
        return ChavePixResponse.de(service.buscar(id));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed(TITULAR)
    public ChavePixResponse trocarConta(@PathParam("id") Long id, @Valid @NotNull TrocaContaRequest request) {
        return ChavePixResponse.de(service.trocarConta(id, request));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({TITULAR, ATENDENTE})
    public void remover(@PathParam("id") Long id) {
        service.remover(id);
    }
}
