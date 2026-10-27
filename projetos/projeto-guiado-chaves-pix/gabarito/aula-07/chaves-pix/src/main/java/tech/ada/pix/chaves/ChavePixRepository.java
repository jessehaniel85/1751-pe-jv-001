package tech.ada.pix.chaves;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Aula 3: padrão Repository do Panache. persist, findByIdOptional, listAll e deleteById
 * vêm prontos de PanacheRepository; aqui ficam só as consultas do domínio.
 */
@ApplicationScoped
public class ChavePixRepository implements PanacheRepository<ChavePix> {

    public List<ChavePix> findByTitular(String titularCpf) {
        return list("titularCpf", titularCpf);
    }

    public long countByConta(String agencia, String numeroConta) {
        return count("agencia = ?1 and numeroConta = ?2", agencia, numeroConta);
    }

    public boolean existeChave(TipoChave tipo, String valor) {
        return count("tipo = ?1 and valor = ?2", tipo, valor) > 0;
    }
}
