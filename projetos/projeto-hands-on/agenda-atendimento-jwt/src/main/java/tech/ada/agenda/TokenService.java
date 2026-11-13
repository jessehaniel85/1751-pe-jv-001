package tech.ada.agenda;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TokenService {

  private final UsuarioRepository usuarios;
  private final long validadeEmSegundos;

  public TokenService(UsuarioRepository usuarios,
    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan") long validadeEmSegundos) {
    this.usuarios = usuarios;
    this.validadeEmSegundos = validadeEmSegundos;
  }

  /** Troca login e senha por um token. Usuário inexistente e senha errada dão a mesma resposta, de propósito. */
  public TokenResponse emitir(String login, String senha) {
    Usuario usuario = this.usuarios.findByLogin(login)
      .filter(encontrado -> BcryptUtil.matches(senha, encontrado.getSenha()))
      .orElseThrow(CredencialInvalidaException::new);
    String token = Jwt.upn(usuario.getLogin())                // quem é: vira o nome do SecurityIdentity
      .groups(Set.of(usuario.getPapeis().split(",")))         // o que pode: vira os papéis do @RolesAllowed
      .sign();                                                // iss e validade: configuração; iat e jti: biblioteca
    return new TokenResponse(token, "Bearer", this.validadeEmSegundos);
  }

}
