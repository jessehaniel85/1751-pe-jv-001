package tech.ada.pix.chaves;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Regras de negócio do diretório de chaves.
 */
@ApplicationScoped
public class ChavePixService {

    @Inject
    ChavePixRepository repository;

    @Inject
    PixConfig config;

    @Inject
    MeterRegistry registry;

    @Inject
    ContasGateway contas;

    @Transactional
    public ChavePix cadastrar(NovaChaveRequest request) {
        String valor = valorDaChave(request);
        exigirChaveInedita(request.tipo(), valor);
        exigirVagaNaConta(request.agencia(), request.numeroConta());
        // Regras locais primeiro; a chamada remota só acontece se elas passarem.
        contas.confirmarConta(request.agencia(), request.numeroConta());
        ChavePix chave = new ChavePix();
        chave.tipo = request.tipo();
        chave.valor = valor;
        chave.agencia = request.agencia();
        chave.numeroConta = request.numeroConta();
        chave.titularCpf = request.titularCpf();
        chave.criadaEm = LocalDateTime.now();
        repository.persist(chave);
        registry.counter("pix.chaves.cadastradas", "tipo", chave.tipo.name()).increment();
        return chave;
    }

    public List<ChavePix> listar(String titularCpf) {
        return titularCpf == null ? repository.listAll() : repository.findByTitular(titularCpf);
    }

    public ChavePix buscar(Long id) {
        return repository.findByIdOptional(id).orElseThrow(() -> new ChaveNaoEncontradaException(id));
    }

    @Transactional
    public ChavePix trocarConta(Long id, TrocaContaRequest request) {
        ChavePix chave = buscar(id);
        if (!jaEstaNaConta(chave, request.agencia(), request.numeroConta())) {
            // A própria chave não conta no limite: "trocar" para a conta em que ela já está não ocupa vaga nova.
            exigirVagaNaConta(request.agencia(), request.numeroConta());
        }
        contas.confirmarConta(request.agencia(), request.numeroConta());
        chave.agencia = request.agencia();
        chave.numeroConta = request.numeroConta();
        // Sem save(): a entidade está gerenciada e o Hibernate grava a mudança no commit.
        return chave;
    }

    @Transactional
    public void remover(Long id) {
        if (!repository.deleteById(id)) {
            throw new ChaveNaoEncontradaException(id);
        }
    }

    private String valorDaChave(NovaChaveRequest request) {
        return request.tipo() == TipoChave.ALEATORIA ? UUID.randomUUID().toString() : request.valor();
    }

    private void exigirChaveInedita(TipoChave tipo, String valor) {
        if (repository.existeChave(tipo, valor)) {
            throw new ChaveDuplicadaException(tipo, valor);
        }
    }

    private void exigirVagaNaConta(String agencia, String numeroConta) {
        int limite = config.chaves().limitePorConta();
        if (repository.countByConta(agencia, numeroConta) >= limite) {
            throw new LimiteDeChavesException(agencia, numeroConta, limite);
        }
    }

    private static boolean jaEstaNaConta(ChavePix chave, String agencia, String numeroConta) {
        return chave.agencia.equals(agencia) && chave.numeroConta.equals(numeroConta);
    }
}
