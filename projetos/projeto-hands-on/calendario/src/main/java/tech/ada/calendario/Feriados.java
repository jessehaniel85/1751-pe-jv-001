package tech.ada.calendario;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.Optional;

/**
 * Aula 5: feriados nacionais de data fixa e um feriado municipal de exemplo. Dados didáticos, em memória:
 * o assunto da aula é quem chama este serviço, não o calendário em si.
 */
@ApplicationScoped
public class Feriados {

  private static final Map<MonthDay, String> NACIONAIS = Map.of(
    MonthDay.of(1, 1), "Confraternização Universal",
    MonthDay.of(4, 21), "Tiradentes",
    MonthDay.of(5, 1), "Dia do Trabalho",
    MonthDay.of(9, 7), "Independência",
    MonthDay.of(10, 12), "Nossa Senhora Aparecida",
    MonthDay.of(11, 2), "Finados",
    MonthDay.of(11, 15), "Proclamação da República",
    MonthDay.of(11, 20), "Dia Nacional de Zumbi e da Consciência Negra",
    MonthDay.of(12, 25), "Natal");

  // Exemplo: só a agência 0001 tem feriado municipal nesta data.
  private static final Map<String, LocalDate> MUNICIPAIS = Map.of("0001", LocalDate.of(2026, 12, 8));

  public DiaDoCalendario consultar(String agencia, LocalDate data) {
    return motivoSemAtendimento(agencia, data)
      .map(motivo -> new DiaDoCalendario(data, false, motivo))
      .orElse(new DiaDoCalendario(data, true, null));
  }

  private Optional<String> motivoSemAtendimento(String agencia, LocalDate data) {
    if (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return Optional.of("Fim de semana");
    }
    if (data.equals(MUNICIPAIS.get(agencia))) {
      return Optional.of("Feriado municipal");
    }
    return Optional.ofNullable(NACIONAIS.get(MonthDay.from(data)));
  }
}
