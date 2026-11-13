package tech.ada.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"agencia", "data", "horario"}))
public class Agendamento {

  @Id
  @GeneratedValue
  private Long id;
  @Column(nullable = false)
  private String agencia;
  @Column(nullable = false)
  private String clienteCpf;
  @Column(nullable = false)
  private LocalDate data;
  @Column(nullable = false)
  private LocalTime horario;

}
