package tech.ada.pix.chaves;

import java.util.List;

/**
 * Aula 6: corpo único para toda resposta de erro da API.
 *
 * @param status   código HTTP, repetido no corpo para quem só lê o JSON (logs, filas)
 * @param erro     nome curto do erro
 * @param mensagens o que corrigir; na validação, uma por campo ("campo: mensagem")
 */
public record ErroResposta(int status, String erro, List<String> mensagens) {

    public static ErroResposta de(int status, String erro, String mensagem) {
        return new ErroResposta(status, erro, List.of(mensagem));
    }
}
