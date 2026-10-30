package tech.ada.agenda;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ApplicationScoped
public class AgendamentoService {

  private final AgendamentoRepository repository;

//  public AgendamentoService(AgendamentoRepository repository) {
//    this.repository = repository;
//  }

  public Agendamento agendar(NovoAgendamentoRequest request) {
    this.exigirHorarioLivre(request.agencia(), request.data(), request.horario(), null);
    Agendamento agendamento = new Agendamento();
    agendamento.setAgencia(request.agencia());
    agendamento.setClienteCpf(request.clienteCpf());
    agendamento.setHorario(request.horario());
    agendamento.setData(request.data());
    //persistir pelo repositorio
    this.repository.persist(agendamento);
    return agendamento;
  }

  private void exigirHorarioLivre(String agencia, LocalDate data, LocalTime horario, Long idQueRemarca) {
    // Remarcar para o próprio horário não é conflito: o horário ocupado é do mesmo agendamento.
    repository.findByHorario(agencia, data, horario)
      .filter(ocupante -> !ocupante.getId().equals(idQueRemarca))
      .ifPresent(ocupante -> {
        throw new HorarioOcupadoException(agencia, data, horario);
      });
  }

  public List<Agendamento> listar(String clienteCpf) {
    return clienteCpf == null ? repository.listAll() : repository.findByCliente(clienteCpf);
  }

  public Agendamento buscar(Long id) {
    return repository.findByIdOptional(id).orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
  }

  public Agendamento remarcar(Long id, RemarcacaoRequest request) {
    Agendamento agendamento = buscar(id);
    this.exigirHorarioLivre(agendamento.getAgencia(), request.data(), request.horario(), id);
    agendamento.setData(request.data());
    agendamento.setHorario(request.horario());
    return agendamento;
  }

  public void cancelar(Long id) {
    if (!repository.deleteById(id)) {
      throw new AgendamentoNaoEncontradoException(id);
    }
  }

  public List<LocalTime> horariosOcupados(String agencia, LocalDate data) {
    return repository.horariosOcupados(agencia, data);
  }

}
