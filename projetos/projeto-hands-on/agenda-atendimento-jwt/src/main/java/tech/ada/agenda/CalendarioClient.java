package tech.ada.agenda;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/agencias")
@RegisterRestClient(configKey = "calendario")
@Produces(MediaType.APPLICATION_JSON)
public interface CalendarioClient {

  @GET
  @Path("/{agencia}/dias/{data}")
  DiaDoCalendario consultar(@PathParam("agencia") String agencia, @PathParam("data") LocalDate data);

}
