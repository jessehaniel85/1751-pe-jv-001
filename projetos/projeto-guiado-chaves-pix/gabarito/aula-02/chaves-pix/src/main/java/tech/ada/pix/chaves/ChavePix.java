package tech.ada.pix.chaves;

import java.time.LocalDateTime;

/**
 * Aula 2: modelo de domínio em memória, com campos privados e getters/setters.
 * Na Aula 3 vira entidade Panache (e os campos ficam públicos: por quê?).
 */
public class ChavePix {

    private Long id;
    private TipoChave tipo;
    private String valor;
    private String agencia;
    private String numeroConta;
    private String titularCpf;
    private LocalDateTime criadaEm;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoChave getTipo() {
        return tipo;
    }

    public void setTipo(TipoChave tipo) {
        this.tipo = tipo;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getAgencia() {
        return agencia;
    }

    public void setAgencia(String agencia) {
        this.agencia = agencia;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public void setNumeroConta(String numeroConta) {
        this.numeroConta = numeroConta;
    }

    public String getTitularCpf() {
        return titularCpf;
    }

    public void setTitularCpf(String titularCpf) {
        this.titularCpf = titularCpf;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public void setCriadaEm(LocalDateTime criadaEm) {
        this.criadaEm = criadaEm;
    }
}
