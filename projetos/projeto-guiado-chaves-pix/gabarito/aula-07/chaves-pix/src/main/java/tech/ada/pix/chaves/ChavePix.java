package tech.ada.pix.chaves;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Aula 3: entidade Panache. PanacheEntity já traz o id (Long, gerado por sequence).
 * Os campos são públicos: o Quarkus gera getters/setters em tempo de build e reescreve
 * todo acesso ao campo para passar por eles.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "valor"}))
public class ChavePix extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TipoChave tipo;

    @Column(nullable = false)
    public String valor;

    @Column(nullable = false)
    public String agencia;

    @Column(nullable = false)
    public String numeroConta;

    @Column(nullable = false)
    public String titularCpf;

    @Column(nullable = false)
    public LocalDateTime criadaEm;
}
