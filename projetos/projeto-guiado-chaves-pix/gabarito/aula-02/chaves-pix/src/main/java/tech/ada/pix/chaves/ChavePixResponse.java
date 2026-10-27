package tech.ada.pix.chaves;

import java.time.LocalDateTime;

/**
 * O que a API devolve para quem consulta uma chave.
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
        return new ChavePixResponse(chave.getId(), chave.getTipo(), chave.getValor(), chave.getAgencia(),
                chave.getNumeroConta(), chave.getTitularCpf(), chave.getCriadaEm());
    }
}
