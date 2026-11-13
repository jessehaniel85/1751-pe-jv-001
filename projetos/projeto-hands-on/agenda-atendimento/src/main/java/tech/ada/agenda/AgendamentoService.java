package tech.ada.agenda;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ApplicationScoped
public class AgendamentoService {

  private static final String AGENDAMENTO_DE_OUTRO = "O agendamento pertence a outro cliente";
  private static final String LISTA_DE_OUTRO = "Cliente só lista os próprios agendamentos";

  private final AgendamentoRepository repository;
  private final AgendaConfig agendaConfig;
  private final MeterRegistry registry;
  private final CalendarioGateway calendarioGateway;
  private final SecurityIdentity identity;

  @Transactional
  public Agendamento agendar(NovoAgendamentoRequest request) {
    this.exigirPosse(request.clienteCpf(), AGENDAMENTO_DE_OUTRO);
    this.exigirDiaDeAtendimento(request.agencia(), request.data());
    this.exigirHorarioLivre(request.agencia(), request.data(), request.horario(), null);
    this.exigirVagaDoCliente(request.clienteCpf());
    Agendamento agendamento = new Agendamento();
    agendamento.setAgencia(request.agencia());
    agendamento.setClienteCpf(request.clienteCpf());
    agendamento.setHorario(request.horario());
    agendamento.setData(request.data());
    //persistir pelo repositorio
    this.repository.persist(agendamento);
    // Aula 4: métrica de negócio. Tag agencia: quantos valores ela pode ter? (cardinalidade, ver aula-04.md)
    this.registry.counter("agenda.agendamentos.criados", "agencia", agendamento.getAgencia()).increment();
    return agendamento;
  }

  private void exigirDiaDeAtendimento(String agencia, LocalDate data) {
    final var diaDoCalendario = this.calendarioGateway.consultar(agencia, data);
    if (!diaDoCalendario.util()) {
      throw new DiaSemAtendimentoException(agencia, data, diaDoCalendario.motivo());
    }
  }

  private void exigirHorarioLivre(String agencia, LocalDate data, LocalTime horario, Long idQueRemarca) {
    // Remarcar para o próprio horário não é conflito: o horário ocupado é do mesmo agendamento.
    repository.findByHorario(agencia, data, horario)
      .filter(ocupante -> !ocupante.getId().equals(idQueRemarca))
      .ifPresent(ocupante -> {
        throw new HorarioOcupadoException(agencia, data, horario);
      });
  }

  private void exigirVagaDoCliente(String clienteCpf) {
    final var limitePorCliente = this.agendaConfig.limitePorCliente();
    if (this.repository.countByClient(clienteCpf) >= limitePorCliente) {
      throw new LimiteDeAgendamentoException(clienteCpf, limitePorCliente);
    }
  }

  public List<Agendamento> listar(String clienteCpf) {
    if (clienteCpf != null) {
      this.exigirPosse(clienteCpf, LISTA_DE_OUTRO);
    }
    String filtro = this.ehUsuario() ? this.usuarioLogado() : clienteCpf;
    return filtro == null ? repository.listAll() : repository.findByCliente(filtro);
  }

  public Agendamento buscar(Long id) {
    final var agendamento = repository.findByIdOptional(id).orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
    this.exigirPosse(agendamento.getClienteCpf(), AGENDAMENTO_DE_OUTRO);
    return agendamento;
  }

  public List<Agendamento> agendaDoDia(String agencia, LocalDate data) {
    return repository.findByAgenciaEData(agencia, data);
  }

  @Transactional
  public Agendamento remarcar(Long id, RemarcacaoRequest request) {
    Agendamento agendamento = buscar(id);
    this.exigirHorarioLivre(agendamento.getAgencia(), request.data(), request.horario(), id);
    agendamento.setData(request.data());
    agendamento.setHorario(request.horario());
    return agendamento;
  }

  @Transactional
  public void cancelar(Long id) {
    // Aula 8: busca antes de apagar, para conferir o dono (o deleteById apagaria sem olhar).
    repository.delete(buscar(id));
  }

  public List<LocalTime> horariosOcupados(String agencia, LocalDate data) {
    return repository.horariosOcupados(agencia, data);
  }

  /** Regra de posse: vale para o papel titular (username = CPF); atendente e auditor passam. */
  private void exigirPosse(String titularCpf, String mensagem) {
    if (ehUsuario() && !usuarioLogado().equals(titularCpf)) {
      throw new ForbiddenException(mensagem);
    }
  }

  private boolean ehUsuario() {
    return identity.hasRole(Papel.USUARIO);
  }

  private String usuarioLogado() {
    return identity.getPrincipal().getName();
  }

}
