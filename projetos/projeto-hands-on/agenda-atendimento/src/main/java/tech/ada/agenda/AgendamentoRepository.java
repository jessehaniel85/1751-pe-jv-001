package tech.ada.agenda;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AgendamentoRepository implements PanacheRepository<Agendamento> {

  public List<Agendamento> findByCliente(String clienteCpf) {
    return list("clienteCpf", clienteCpf);
  }

  public Optional<Agendamento> findByHorario(String agencia, LocalDate data, LocalTime horario) {
    return find("agencia = ?1 and data = ?2 and horario = ?3", agencia, data, horario).firstResultOptional();
  }

  public List<Agendamento> findByAgenciaEData(String agencia, LocalDate data) {
    return list("agencia = ?1 and data = ?2", Sort.by("horario"), agencia, data);
  }

  public List<LocalTime> horariosOcupados(String agencia, LocalDate data) {
    return findByAgenciaEData(agencia, data).stream()
      .map(Agendamento::getHorario)
      .toList();
  }

  public long countByClient(String clienteCpf) {
    return count("clienteCpf", clienteCpf);
  }
}
