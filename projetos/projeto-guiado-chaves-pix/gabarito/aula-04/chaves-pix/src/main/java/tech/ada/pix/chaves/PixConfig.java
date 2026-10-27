package tech.ada.pix.chaves;

import io.smallrye.config.ConfigMapping;

/**
 * Aula 4: configuração tipada do domínio (equivale a um @ConfigurationProperties do Spring).
 * O nome do método vira kebab-case: limitePorConta lê pix.chaves.limite-por-conta.
 * Sem a propriedade, a aplicação não sobe: o erro aparece no boot, não na primeira requisição.
 */
@ConfigMapping(prefix = "pix")
public interface PixConfig {

    Chaves chaves();

    interface Chaves {

        /** Regra do DICT para pessoa física: até 5 chaves por conta. */
        int limitePorConta();
    }
}
