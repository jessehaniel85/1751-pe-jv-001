package tech.ada.pix.chaves;

import java.time.LocalDateTime;

/**
 * O que a API devolve para quem consulta uma chave. A entidade não sai pela API.
 */
public record ChavePixResponse(
        Long id,
        TipoChave tipo,
        String valor,
        String agencia,
        String numeroConta,
        String titularCpf,
        LocalDateTime criadaEm) {

    public static ChavePixResponse de(ChavePix chave) {
        return new ChavePixResponse(chave.id, chave.tipo, chave.valor, chave.agencia,
                chave.numeroConta, chave.titularCpf, chave.criadaEm);
    }
}
