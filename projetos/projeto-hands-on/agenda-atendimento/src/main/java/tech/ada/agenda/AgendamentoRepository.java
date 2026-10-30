package tech.ada.agenda;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class AgendamentoRepository {

  //persistência em memória
  private final Map<Long, Agendamento> agendamentos = new ConcurrentHashMap<>();
  private final AtomicLong sequencia = new AtomicLong(0L);

  public void persist(Agendamento agendamento) {
    agendamento.setId(sequencia.incrementAndGet());//++sequencia
    agendamentos.put(agendamento.getId(), agendamento);
  }

  public Optional<Agendamento> findByIdOptional(Long id) {
    return Optional.ofNullable(agendamentos.get(id));
  }

  public List<Agendamento> listAll() {
    return List.copyOf(agendamentos.values());
  }

  public List<Agendamento> findByCliente(String clienteCpf) {
    return agendamentos.values().stream()
      .filter(agendamento -> agendamento.getClienteCpf().equals(clienteCpf))
      .toList();
  }

  public Optional<Agendamento> findByHorario(String agencia, LocalDate data, LocalTime horario) {
    return agendamentos.values().stream()
      .filter(agendamento -> agendamento.getAgencia().equals(agencia)
        && agendamento.getData().equals(data)
        && agendamento.getHorario().equals(horario))
      .findFirst();
  }

  public List<LocalTime> horariosOcupados(String agencia, LocalDate data) {
    return agendamentos.values().stream()
      .filter(agendamento -> agendamento.getAgencia().equals(agencia) && agendamento.getData().equals(data))
      .map(Agendamento::getHorario)
      .toList();
  }

  public boolean deleteById(Long id) {
    return agendamentos.remove(id) != null;
  }

}
