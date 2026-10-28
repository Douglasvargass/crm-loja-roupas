package br.senai.crmloja.modules.crm.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    // Os mesmos CPFs de teste do DevDataSeeder: se algum falhar aqui, o seed falharia no boot.
    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "39053344705", "87748248800", "71428793860", "34608514300"})
    void aceitaCpfsValidos(String cpf) {
        assertThat(Cpf.isValid(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111111", "00000000000", "52998224724", "1234567890", "abcdefghijk", ""})
    void recusaCpfsInvalidos(String cpf) {
        assertThat(Cpf.isValid(cpf)).isFalse();
    }
}
