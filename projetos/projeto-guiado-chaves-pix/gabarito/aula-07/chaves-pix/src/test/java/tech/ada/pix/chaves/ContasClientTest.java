package tech.ada.pix.chaves;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

/**
 * Teste de unidade do mapeamento de erros do cliente REST (sem subir o Quarkus).
 */
class ContasClientTest {

    @Test
    void status404ViraContaInexistente() {
        assertThat(ContasClient.mapear(Response.status(404).build()), instanceOf(ContaInexistenteException.class));
    }

    @Test
    void outrosStatusFicamComOMapeamentoPadrao() {
        assertThat(ContasClient.mapear(Response.status(500).build()), nullValue());
    }
}
