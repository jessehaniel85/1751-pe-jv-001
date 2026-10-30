package tech.ada.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

//Na aula 3 vai ser nossa entidade JPA
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Agendamento {

  private Long id;
  private String agencia;
  private String clienteCpf;
  private LocalDate data;
  private LocalTime horario;

}
