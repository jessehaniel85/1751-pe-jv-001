package tech.ada.agenda;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgendamentoRepositoryTest {

  private static final LocalDate DATA = LocalDate.of(2026, 10, 26);

  @Inject
  AgendamentoRepository repository;

  @Test
  void findByCliente() {
  }

  @Test
  @TestTransaction
  void findByHorario() {
    repository.persist(agendamento("00001", LocalTime.of(10, 0)));
    assertTrue(repository.findByHorario("00001", DATA, LocalTime.of(10, 0)).isPresent());
    assertTrue(repository.findByHorario("00001", DATA, LocalTime.of(10, 30)).isEmpty());
    assertTrue(repository.findByHorario("00002", DATA, LocalTime.of(10, 0)).isEmpty());
  }

  @Test
  @TestTransaction
  void horariosOcupadosVemEmOrdem() {
    repository.persist(agendamento("0008", LocalTime.of(15, 0)));
    repository.persist(agendamento("0008", LocalTime.of(10, 30)));

    assertEquals(List.of(LocalTime.of(10, 30), LocalTime.of(15, 0)), repository.horariosOcupados("0008", DATA));
  }

  @Test
  @TestTransaction
  void bancoRecusaDoisAgendamentosNoMesmoHorario() {
    repository.persistAndFlush(agendamento("0007", LocalTime.of(11, 0)));

    // Sem passar pelo serviço: quem recusa é a restrição única da tabela.
    assertThrows(PersistenceException.class,
      () -> repository.persistAndFlush(agendamento("0007", LocalTime.of(11, 0))));
  }


  private static Agendamento agendamento(String agencia, LocalTime horario) {
    return Agendamento.builder()
        .agencia(agencia)
        .horario(horario)
        .data(DATA)
        .clienteCpf("605.198.540-98")
        .build();
  }
}
