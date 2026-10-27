package tech.ada.pix.chaves;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * Aula 7: cria os usuários de aula quando a aplicação sobe (o import.sql não serve:
 * a senha precisa passar pelo BCrypt antes de ir para o banco).
 * Senhas de aula, conhecidas de todos: em produção, usuários vêm de cadastro ou de um provedor de identidade.
 */
@ApplicationScoped
public class CargaDeUsuarios {

    @Transactional
    void carregar(@Observes StartupEvent evento) {
        Usuario.deleteAll();
        Usuario.adicionar("52998224725", "titular123", "titular");
        Usuario.adicionar("11144477735", "titular123", "titular");
        Usuario.adicionar("12345678909", "titular123", "titular");
        Usuario.adicionar("atendente", "atendente123", "atendente");
        Usuario.adicionar("auditor", "auditor123", "auditor");
    }
}
