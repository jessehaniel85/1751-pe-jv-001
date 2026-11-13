package tech.ada.agenda;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

//Quais horários a agência tem livres amanhã ou numa data específica?
//GET /agencias/{codigo}/horarios?data=
@Path("/agencias/{codigo}/horarios")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class HorarioResource {

  @Inject
  AgendamentoService service;

  @Inject
  AgendaConfig agendaConfig;

  @GET
  public HorariosDoDia listar(@PathParam("codigo") String agencia,
    @QueryParam("data") LocalDate data) {//ISO 8601 -> yyyy-MM-dd
    LocalDate dataHorario = Optional.ofNullable(data)
      .orElseGet(() -> LocalDate.now().plusDays(1));
    List<LocalTime> livres = agendaConfig.expediente().horarios();
    livres.removeAll(service.horariosOcupados(agencia, dataHorario));
    return new HorariosDoDia(agencia, dataHorario, livres);
  }

}
