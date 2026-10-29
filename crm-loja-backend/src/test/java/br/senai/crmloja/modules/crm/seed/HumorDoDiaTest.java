package br.senai.crmloja.modules.crm.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

// Spec crm/08: o que vem da IA é limitado antes de virar venda. Nada fora da faixa chega ao motor.
class HumorDoDiaTest {

    final ObjectMapper json = new ObjectMapper();

    @Test
    void respostaBoaViraHumorDaIa() {
        var h = HumorDoDia.daResposta("""
                {"fator": 1.25, "categoria_destaque": "Calças", "fator_categoria": 1.5,
                 "motivo": "Sexta de pagamento: shopping cheio e procura por calças."}""", json).orElseThrow();
        assertThat(h.fator()).isEqualTo(1.25);
        assertThat(h.familiaDestaque()).isEqualTo("CAL");
        assertThat(h.fatorDestaque()).isEqualTo(1.5);
        assertThat(h.motivo()).startsWith("Sexta de pagamento");
        assertThat(h.fonte()).isEqualTo("AI");
    }

    @Test
    void aceitaJsonDentroDeBlocoDeCodigo() {
        var h = HumorDoDia.daResposta("Claro!\n```json\n{\"fator\": 0.9, \"categoria_destaque\": null, "
                + "\"fator_categoria\": 1.0, \"motivo\": \"Segunda fraca.\"}\n```", json);
        assertThat(h).isPresent();
        assertThat(h.get().familiaDestaque()).isNull();
        assertThat(h.get().fatorDestaque()).isEqualTo(1.0);
    }

    @Test
    void numerosForaDaFaixaSaoLimitados() {
        var h = HumorDoDia.daResposta("""
                {"fator": 9, "categoria_destaque": "JAQ", "fator_categoria": 50, "motivo": "Exagero"}""", json).orElseThrow();
        assertThat(h.fator()).isEqualTo(HumorDoDia.FATOR_MAX);
        assertThat(h.fatorDestaque()).isEqualTo(HumorDoDia.DESTAQUE_MAX);
        var baixo = HumorDoDia.daResposta("{\"fator\": -3, \"motivo\": \"x\"}", json).orElseThrow();
        assertThat(baixo.fator()).isEqualTo(HumorDoDia.FATOR_MIN);
    }

    @Test
    void categoriaDesconhecidaFicaSemDestaque() {
        var h = HumorDoDia.daResposta("{\"fator\": 1.0, \"categoria_destaque\": \"Sapatos\", "
                + "\"fator_categoria\": 1.7, \"motivo\": \"x\"}", json).orElseThrow();
        assertThat(h.familiaDestaque()).isNull();
        assertThat(h.fatorDestaque()).isEqualTo(1.0);
        assertThat(HumorDoDia.familia("acessórios")).isEqualTo("ACE");
        assertThat(HumorDoDia.familia("Vestidos")).isEqualTo("VES");
    }

    @Test
    void respostaInutilVoltaVaziaEOAgenteUsaOPadrao() {
        assertThat(HumorDoDia.daResposta(null, json)).isEmpty();
        assertThat(HumorDoDia.daResposta("Não posso ajudar com isso.", json)).isEmpty();
        assertThat(HumorDoDia.daResposta("{\"fator\": \"alto\", \"motivo\": \"x\"}", json)).isEmpty();
        assertThat(HumorDoDia.daResposta("{\"fator\": 1.1, \"motivo\": \"   \"}", json)).isEmpty();
        assertThat(HumorDoDia.padrao("Dia comum").fonte()).isEqualTo("DEFAULT");
    }

    @Test
    void motivoViraUmaLinhaCurtaSemMarcacao() {
        String longo = "**Chuva** forte\n" + "a".repeat(400);
        String m = HumorDoDia.motivo(longo);
        assertThat(m).doesNotContain("\n").doesNotContain("*");
        assertThat(m.length()).isLessThanOrEqualTo(HumorDoDia.MOTIVO_MAX);
        assertThat(m).endsWith("…");
    }
}
