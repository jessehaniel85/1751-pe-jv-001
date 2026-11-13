package tech.ada.agenda;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoginResource {

  private final TokenService service;
  private final JsonWebToken jwt;

  public LoginResource(TokenService service, JsonWebToken jwt) {
    this.service = service;
    this.jwt = jwt;
  }

  // Aberto: é aqui que quem ainda não tem token consegue um.
  @PermitAll
  @POST
  public TokenResponse entrar(@Valid @NotNull LoginRequest request) {
    return this.service.emitir(request.login(), request.senha());
  }

  // Lê as claims do token da requisição corrente.
  @Authenticated
  @GET
  @Path("/eu")
  public QuemSouEuResponse quemSouEu() {
    return new QuemSouEuResponse(this.jwt.getName(), this.jwt.getGroups(), this.jwt.getIssuer(),
      Instant.ofEpochSecond(this.jwt.getExpirationTime()));
  }

}
