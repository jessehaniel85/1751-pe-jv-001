package tech.ada.pix.chaves;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de unidade do dígito verificador do CPF (sem subir o Quarkus).
 */
class ChaveValidaValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "12345678909"})
    void aceitaCpfComDigitosCorretos(String cpf) {
        assertTrue(ChaveValidaValidator.cpfValido(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "12345678900", "11111111111", "529.982.247-25", "5299822472"})
    void recusaCpfComDigitoErradoRepetidoOuFormatado(String cpf) {
        assertFalse(ChaveValidaValidator.cpfValido(cpf));
    }
}
