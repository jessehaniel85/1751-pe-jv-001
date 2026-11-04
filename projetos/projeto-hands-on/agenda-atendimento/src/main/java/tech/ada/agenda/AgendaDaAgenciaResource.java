package tech.ada.agenda;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

//GET /agencias/{codigo}/agendamentos?data=
@Path("/agencias/{codigo}/agendamentos")
@Produces(MediaType.APPLICATION_JSON)
public class AgendaDaAgenciaResource {

  private final AgendamentoService service;

  public AgendaDaAgenciaResource(AgendamentoService service) {
    this.service = service;
  }

  @GET
  public List<AgendamentoResponse> listar(@PathParam("codigo") String agencia, @QueryParam("data") LocalDate data) {
    LocalDate dia = Optional.ofNullable(data).orElseGet(LocalDate::now);
    return service.agendaDoDia(agencia, dia).stream()
      .map(AgendamentoResponse::new)
      .toList();
  }

}
