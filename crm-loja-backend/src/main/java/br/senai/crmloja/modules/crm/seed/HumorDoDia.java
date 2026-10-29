package br.senai.crmloja.modules.crm.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.Map;
import java.util.Optional;

/**
 * O "clima" de um dia de loja, que o agente de demonstração usa para gerar as vendas (spec crm/08).
 * A IA só escolhe estes 3 números e uma frase; quem cria as vendas é o motor do histórico. Por isso tudo o que vem
 * da IA é conferido e limitado aqui: um número fora da faixa ou um texto estranho nunca chega ao banco.
 */
record HumorDoDia(double fator, String familiaDestaque, double fatorDestaque, String motivo, String fonte) {

    static final double FATOR_MIN = 0.60, FATOR_MAX = 1.40;
    static final double DESTAQUE_MIN = 1.00, DESTAQUE_MAX = 1.80;
    static final int MOTIVO_MAX = 200;

    // Nomes de categoria (como a IA escreve) → família do código do produto.
    private static final Map<String, String> FAMILIAS = Map.of(
            "camisetas", "CAM", "camiseta", "CAM", "calcas", "CAL", "calca", "CAL",
            "vestidos", "VES", "vestido", "VES", "jaquetas", "JAQ", "jaqueta", "JAQ",
            "acessorios", "ACE", "acessorio", "ACE");

    /** Dia comum, sem IA: o padrão da loja, sem destaque. */
    static HumorDoDia padrao(String motivo) {
        return new HumorDoDia(1.0, null, 1.0, motivo, "DEFAULT");
    }

    /**
     * Lê a resposta da IA. Aceita o JSON puro ou dentro de um bloco ```json. Qualquer coisa que não dê para ler
     * vira vazio, e o agente usa o dia padrão.
     */
    static Optional<HumorDoDia> daResposta(String resposta, ObjectMapper json) {
        if (resposta == null) return Optional.empty();
        int ini = resposta.indexOf('{');
        int fim = resposta.lastIndexOf('}');
        if (ini < 0 || fim <= ini) return Optional.empty();
        try {
            JsonNode n = json.readTree(resposta.substring(ini, fim + 1));
            if (!n.path("fator").isNumber()) return Optional.empty();
            double fator = limitar(n.path("fator").asDouble(), FATOR_MIN, FATOR_MAX);
            String familia = familia(n.path("categoria_destaque").asText(null));
            double destaque = familia == null ? 1.0
                    : limitar(n.path("fator_categoria").asDouble(1.0), DESTAQUE_MIN, DESTAQUE_MAX);
            String motivo = motivo(n.path("motivo").asText(""));
            if (motivo.isEmpty()) return Optional.empty();
            return Optional.of(new HumorDoDia(arredondar(fator), familia, arredondar(destaque), motivo, "AI"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static String familia(String nome) {
        if (nome == null || nome.isBlank() || nome.equalsIgnoreCase("null")) return null;
        String n = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase();
        if (FAMILIAS.containsKey(n)) return FAMILIAS.get(n);
        String codigo = n.toUpperCase();
        return FAMILIAS.containsValue(codigo) ? codigo : null;
    }

    // Uma linha só, sem marcação, no máximo 200 caracteres: é texto que vai para a tela do gerente.
    static String motivo(String texto) {
        String t = texto == null ? "" : texto.replaceAll("[\\r\\n\\t]+", " ").replaceAll("[*_`#<>]", "").trim();
        return t.length() > MOTIVO_MAX ? t.substring(0, MOTIVO_MAX - 1).trim() + "…" : t;
    }

    private static double limitar(double v, double min, double max) {
        if (Double.isNaN(v)) return 1.0;
        return Math.max(min, Math.min(max, v));
    }

    private static double arredondar(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
