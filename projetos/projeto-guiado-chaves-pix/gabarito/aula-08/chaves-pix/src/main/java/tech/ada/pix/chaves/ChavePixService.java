package tech.ada.pix.chaves;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
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

    private static final String CHAVE_DE_OUTRO = "A chave pertence a outro titular";
    private static final String LISTA_DE_OUTRO = "Titular só lista as próprias chaves";

    @Inject
    ChavePixRepository repository;

    @Inject
    PixConfig config;

    @Inject
    MeterRegistry registry;

    @Inject
    ContasGateway contas;

    @Inject
    SecurityIdentity identidade;

    @Transactional
    public ChavePix cadastrar(NovaChaveRequest request) {
        exigirPosse(request.titularCpf(), CHAVE_DE_OUTRO);
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

    /**
     * Titular vê só as próprias chaves: sem filtro, a lista é a dele; filtro com o CPF de outro titular é 403
     * (mesma regra de posse do GET por id). Atendente e auditor veem todas, com ou sem filtro.
     */
    public List<ChavePix> listar(String titularCpf) {
        if (titularCpf != null) {
            exigirPosse(titularCpf, LISTA_DE_OUTRO);
        }
        String filtro = ehTitular() ? usuarioLogado() : titularCpf;
        return filtro == null ? repository.listAll() : repository.findByTitular(filtro);
    }

    public ChavePix buscar(Long id) {
        ChavePix chave = repository.findByIdOptional(id).orElseThrow(() -> new ChaveNaoEncontradaException(id));
        exigirPosse(chave.titularCpf, CHAVE_DE_OUTRO);
        return chave;
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
        repository.delete(buscar(id));
    }

    /** Regra de posse: vale para o papel titular (username = CPF); atendente e auditor passam. */
    private void exigirPosse(String titularCpf, String mensagem) {
        if (ehTitular() && !usuarioLogado().equals(titularCpf)) {
            throw new ForbiddenException(mensagem);
        }
    }

    private boolean ehTitular() {
        return identidade.hasRole(Papeis.TITULAR);
    }

    private String usuarioLogado() {
        return identidade.getPrincipal().getName();
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
