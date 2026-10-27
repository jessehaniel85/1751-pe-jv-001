package tech.ada.pix.contas;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Optional;

/**
 * Aula 5: conta corrente do serviço de contas. Aqui usamos o padrão Active Record do Panache
 * (a consulta mora na própria entidade), para contrastar com o Repository do módulo chaves-pix.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"agencia", "numero"}))
public class Conta extends PanacheEntity {

    @Column(nullable = false)
    public String agencia;

    @Column(nullable = false)
    public String numero;

    @Column(nullable = false)
    public String titularCpf;

    public static Optional<Conta> buscar(String agencia, String numero) {
        return find("agencia = ?1 and numero = ?2", agencia, numero).firstResultOptional();
    }
}
