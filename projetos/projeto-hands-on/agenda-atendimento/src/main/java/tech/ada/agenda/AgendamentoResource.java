package tech.ada.agenda;

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

  @POST
  public RestResponse<AgendamentoResponse> agendar(NovoAgendamentoRequest request, @Context UriInfo uriInfo) {
    Agendamento agendamento = this.service.agendar(request);
    URI location = uriInfo.getAbsolutePathBuilder().path(agendamento.getId().toString()).build();
    return RestResponse.ResponseBuilder.<AgendamentoResponse>created(location)
      .entity(new AgendamentoResponse(agendamento)).build();
  }

  @GET
  public List<AgendamentoResponse> listar(@QueryParam("cpf") String clienteCpf) {
    return this.service.listar(clienteCpf).stream()
      .map(AgendamentoResponse::new)
      .toList();
  }

  @GET
  @Path("/{id}")
  public AgendamentoResponse buscar(Long id) {
    return new AgendamentoResponse(this.service.buscar(id));
  }

  @PUT
  @Path("/{id}")
  public AgendamentoResponse remarcar(Long id, RemarcacaoRequest request) {
    return new AgendamentoResponse(this.service.remarcar(id, request));
  }

  @DELETE
  @Path("/{id}")
  public void cancelar(Long id) {
    this.service.cancelar(id);
  }

}
