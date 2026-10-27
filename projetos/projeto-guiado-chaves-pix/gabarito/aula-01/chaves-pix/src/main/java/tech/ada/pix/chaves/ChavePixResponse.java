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
}
