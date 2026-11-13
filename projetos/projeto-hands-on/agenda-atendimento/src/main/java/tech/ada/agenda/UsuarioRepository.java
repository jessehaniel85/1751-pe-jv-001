package tech.ada.agenda;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class UsuarioRepository implements PanacheRepository<Usuario> {

  public Optional<Usuario> findByLogin(String login) {
    return find("login", login).firstResultOptional();
  }
}
