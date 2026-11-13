package tech.ada.agenda;

import static tech.ada.agenda.Papel.ATENDENTE;
import static tech.ada.agenda.Papel.GERENTE;
import static tech.ada.agenda.Papel.USUARIO;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;

@DenyAll
@Path("/agendamentos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AgendamentoResource {

  /*
  Crie os endpoints para criar, listar (por cpf), buscar (por id), remarcar e cancelar um agendamento.
  Não deve ser permitido dois agendamentos no mesmo horário.
  Persistência em List, Map, ou outra da preferência.
   */

//  @Inject
//  AgendamentoService service;

  private final AgendamentoService service;

  //  @Inject
  public AgendamentoResource(AgendamentoService service) {
    this.service = service;
  }

  @RolesAllowed({USUARIO, ATENDENTE})
  @POST
  public RestResponse<AgendamentoResponse> agendar(@Valid @NotNull NovoAgendamentoRequest request, @Context UriInfo uriInfo) {
    Agendamento agendamento = this.service.agendar(request);
    URI location = uriInfo.getAbsolutePathBuilder().path(agendamento.getId().toString()).build();
    return RestResponse.ResponseBuilder.<AgendamentoResponse>created(location)
      .entity(new AgendamentoResponse(agendamento)).build();
  }

  @RolesAllowed({USUARIO, ATENDENTE, GERENTE})
  @GET
  public List<AgendamentoResponse> listar(@QueryParam("cpf") String clienteCpf) {
    return this.service.listar(clienteCpf).stream()
      .map(AgendamentoResponse::new)
      .toList();
  }

  @RolesAllowed({USUARIO, ATENDENTE, GERENTE})
  @GET
  @Path("/{id}")
  public AgendamentoResponse buscar(Long id) {
    return new AgendamentoResponse(this.service.buscar(id));
  }

  @RolesAllowed({USUARIO, ATENDENTE})
  @PUT
  @Path("/{id}")
  public AgendamentoResponse remarcar(Long id, RemarcacaoRequest request) {
    return new AgendamentoResponse(this.service.remarcar(id, request));
  }

  @RolesAllowed({USUARIO, ATENDENTE})
  @DELETE
  @Path("/{id}")
  public void cancelar(Long id) {
    this.service.cancelar(id);
  }

}
