package tech.ada.pix.chaves;

/**
 * Aula 8: nomes dos papéis. Constantes (e não enum) porque anotações como @RolesAllowed só aceitam String constante.
 */
public final class Papeis {

    /** Dono das chaves; o username é o CPF. */
    public static final String TITULAR = "titular";
    /** Atendimento do banco: lê qualquer chave e remove a pedido do cliente. */
    public static final String ATENDENTE = "atendente";
    /** Auditoria: só leitura. */
    public static final String AUDITOR = "auditor";

    private Papeis() {
    }
}
