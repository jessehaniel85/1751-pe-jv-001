package tech.ada.calendario;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/agencias/{agencia}/dias/{data}")
@Produces(MediaType.APPLICATION_JSON)
public class CalendarioResource {

  private final Feriados feriados;
  private final long atrasoMs;

  public CalendarioResource(Feriados feriados, @ConfigProperty(name = "calendario.atraso-ms") long atrasoMs) {
    this.feriados = feriados;
    this.atrasoMs = atrasoMs;
  }

  @GET
  public DiaDoCalendario consultar(@PathParam("agencia") String agencia, @PathParam("data") LocalDate data)
    throws InterruptedException {
    Thread.sleep(atrasoMs); // Aula 5: simula o serviço lento (calendario.atraso-ms)
    return feriados.consultar(agencia, data);
  }
}
