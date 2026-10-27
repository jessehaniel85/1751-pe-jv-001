package tech.ada.pix.chaves;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aula 2: repositório em memória. Os nomes dos métodos imitam os do Panache
 * (persist, findByIdOptional, listAll, deleteById) para que a troca da Aula 3 seja pequena.
 */
@ApplicationScoped
public class ChavePixRepository {

    private final Map<Long, ChavePix> chaves = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public void persist(ChavePix chave) {
        chave.setId(sequencia.incrementAndGet());
        chaves.put(chave.getId(), chave);
    }

    public Optional<ChavePix> findByIdOptional(Long id) {
        return Optional.ofNullable(chaves.get(id));
    }

    public List<ChavePix> listAll() {
        return List.copyOf(chaves.values());
    }

    public List<ChavePix> findByTitular(String titularCpf) {
        return chaves.values().stream()
                .filter(chave -> chave.getTitularCpf().equals(titularCpf))
                .toList();
    }

    public long countByConta(String agencia, String numeroConta) {
        return chaves.values().stream()
                .filter(chave -> chave.getAgencia().equals(agencia) && chave.getNumeroConta().equals(numeroConta))
                .count();
    }

    public boolean existeChave(TipoChave tipo, String valor) {
        return chaves.values().stream()
                .anyMatch(chave -> chave.getTipo() == tipo && chave.getValor().equals(valor));
    }

    public boolean deleteById(Long id) {
        return chaves.remove(id) != null;
    }
}
