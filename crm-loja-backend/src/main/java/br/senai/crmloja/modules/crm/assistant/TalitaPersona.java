package br.senai.crmloja.modules.crm.assistant;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

// Respostas fixas da Talita para o que não precisa de IA: "oi" e "quem é você?". Sai na hora, não gasta
// a cota do provedor (importante nos planos grátis) e continua funcionando quando o limite acaba.
final class TalitaPersona {

    private TalitaPersona() {}

    private static final Set<String> GREETING_WORDS = Set.of(
            "oi", "oie", "oii", "ola", "opa", "ei", "hey", "hello", "hi", "eai", "ai", "e", "salve", "fala",
            "bom", "boa", "dia", "tarde", "noite", "tudo", "bem", "td", "certo", "talita", "pessoal", "gente", "voce", "vc");
    // Pelo menos uma destas precisa aparecer, senão "tudo bem" sozinho ou "e" não contariam como cumprimento.
    private static final Set<String> GREETING_CORE = Set.of(
            "oi", "oie", "oii", "ola", "opa", "ei", "hey", "hello", "hi", "eai", "ai", "salve", "fala",
            "dia", "tarde", "noite", "talita");

    // Frase inteira (não trecho): "qual o nome do produto mais vendido?" NÃO é pergunta sobre ela.
    private static final Pattern WHO_ARE_YOU = Pattern.compile(
            "^(?:(?:oi|ola) )?(?:talita )?(?:"
                    + "quem (?:e|eh) (?:voce|vc|a talita)"
                    + "|qual (?:e )?(?:o )?(?:seu )?nome(?: dela)?"
                    + "|como (?:voce|vc) (?:se )?chama"
                    + "|se apresente|apresente se"
                    + "|o que (?:voce|vc) (?:faz|pode fazer)"
                    + "|como (?:voce|vc) pode (?:me )?ajudar"
                    + "|(?:voce|vc) pode (?:me )?ajudar"
                    + "|voce e uma (?:ia|pessoa|robo)"
                    + ")(?: talita)?$");

    // "Conta a história da Orden", "quem criou o sistema?": resposta fixa, igual à do prompt (docs/17-nossa-historia.md).
    private static final Pattern STORY = Pattern.compile(
            "\\bhistoria (?:da|do|de) (?:orden|loja|sistema|projeto|app|aplicativo|voces|equipe|talita)\\b"
                    + "|\\b(?:sua|tua) historia\\b"
                    + "|\\bquem (?:criou|fez|desenvolveu|construiu|programou|inventou) (?:o sistema|a orden|o app|o aplicativo|voce|vc|a talita|isso)\\b"
                    + "|\\bcomo (?:surgiu|nasceu|comecou) (?:a orden|o sistema|o projeto|a loja|o app|voce|vc)\\b");
    // Palavras de dado: "história de vendas da loja" é pergunta de dados, não a história da Orden.
    private static final Pattern DATA_WORDS = Pattern.compile("\\b(?:vendas?|compras?|estoque|produtos?|clientes?|pedidos?|faturamento)\\b");

    static boolean isStoryRequest(String message) {
        String n = normalize(message);
        if (n.isEmpty() || n.length() > 90) return false;
        return STORY.matcher(n).find() && !DATA_WORDS.matcher(n).find();
    }

    static String story(String fullName) {
        String first = firstName(fullName);
        return (first.isEmpty() ? "" : first + ", ") + "a Orden nasceu no segundo semestre de 2026, no curso "
                + "**Técnico em Desenvolvimento de Sistemas do SENAI de Tubarão**.\n\n"
                + "A ideia veio de uma dor que quem trabalha no comércio conhece: estoque sem controle faz a loja perder "
                + "dinheiro, porque uma peça pequena e cara pode sumir sem que os donos percebam. Quando o desafio do curso foi "
                + "um sistema para uma loja de roupas, a turma quis resolver exatamente isso.\n\n"
                + "Seis colegas se dividiram:\n"
                + "- **Douglas Vargas**: engenharia do sistema e eu, a Talita;\n"
                + "- **Julia Alcantara**: marketing, a marca e as telas;\n"
                + "- **Guilherme Dower**: o olhar do gerente;\n"
                + "- **Vinicius Clemente**: operação e compras, recebe os chamados de peça em falta e faz o pedido;\n"
                + "- **Vitor Nunes e Vinicius Muniz**: o balcão, os clientes e os testes.\n\n"
                + "Hoje a loja tem vendas, estoque com histórico que não se apaga, metas, chamados de peça em falta e eu, "
                + "para responder com os dados de verdade. Quer ver algum número da loja?";
    }

    static boolean isIntroductionRequest(String message) {
        String n = normalize(message);
        if (n.isEmpty() || n.length() > 60) return false;
        if (WHO_ARE_YOU.matcher(n).matches()) return true;
        String[] words = n.split(" ");
        if (words.length > 6) return false;
        boolean core = false;
        for (String w : words) {
            if (!GREETING_WORDS.contains(w)) return false;
            if (GREETING_CORE.contains(w)) core = true;
        }
        return core;
    }

    static String introduction(String fullName, String role) {
        String first = firstName(fullName);
        // Cliente da loja: apresentação própria, sem falar de "dados da loja" nem de telas internas.
        if ("CUSTOMER".equals(role)) {
            return "Oi" + (first.isEmpty() ? "" : ", " + first) + "! Eu sou a **Talita**, da **Orden**.\n\n"
                    + "Posso te ajudar a encontrar peças, acompanhar os seus pedidos e, se você procura uma peça que não está "
                    + "na vitrine, pedir para a loja. Por exemplo:\n"
                    + "- \"Tem vestido tamanho M?\"\n"
                    + "- \"Como está o meu último pedido?\"\n"
                    + "- \"Queria uma jaqueta jeans G, vocês têm?\"\n\n"
                    + "Em que posso ajudar?";
        }
        String examples = switch (role) {
            case "SELLER" -> """
                    - "Quanto falta para a minha meta?"
                    - "Tem camiseta tamanho M disponível?"
                    - "O que a cliente Ana Paula já comprou?\"""";
            case "STOCK_KEEPER" -> """
                    - "O que está sem estoque?"
                    - "Quais produtos estão abaixo do mínimo?"
                    - "Como registro uma avaria?\"""";
            case "PURCHASING" -> """
                    - "Quais chamados de peça em falta estão abertos?"
                    - "O que está sem estoque?"
                    - "Quais produtos estão abaixo do mínimo?\"""";
            default -> """
                    - "Quanto vendemos este mês e como foi em relação ao período anterior?"
                    - "O que preciso repor com urgência?"
                    - "Qual categoria fatura mais?\"""";
        };
        return "Oi" + (first.isEmpty() ? "" : ", " + first) + "! Eu sou a **Talita**, assistente virtual da **Orden**.\n\n"
                + "Eu consulto os dados reais da loja na hora e também explico como usar o sistema. Você pode me perguntar, por exemplo:\n"
                + examples + "\n\n"
                + "Eu só consulto: para cadastrar ou alterar algo, te mostro o caminho na tela. Em que posso ajudar?";
    }

    static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        return fullName.trim().split("\\s+")[0];
    }

    // minúsculas, sem acento nem pontuação, espaços únicos: "Olá, Talita!!" → "ola talita".
    static String normalize(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
