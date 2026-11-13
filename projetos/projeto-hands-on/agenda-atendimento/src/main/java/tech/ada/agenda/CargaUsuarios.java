package tech.ada.agenda;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ApplicationScoped
public class CargaUsuarios {

  private final UsuarioRepository repository;

  @Transactional
  public void carregar(@Observes StartupEvent event) {
    this.repository.deleteAll();
    this.repository.persist(
      List.of(
        Usuario.adicionar("usuario1", "senha123", "usuario"),
        Usuario.adicionar("usuario2", "senha123", "usuario"),
        Usuario.adicionar("atendente", "atendente123", "atendente"),
        Usuario.adicionar("gerente", "gerente123", "gerente")
      )
    );
  }

}
