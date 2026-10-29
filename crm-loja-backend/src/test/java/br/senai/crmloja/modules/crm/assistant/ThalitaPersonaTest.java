package br.senai.crmloja.modules.crm.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThalitaPersonaTest {

    @ParameterizedTest
    @ValueSource(strings = {"oi", "Oi!", "  OI  ", "oii", "olá", "Olá, Thalita!", "oi talita", "oi thalita", "quem é a talita?", "bom dia", "Boa tarde!", "boa noite talita",
            "e aí", "eai", "oi tudo bem", "oi, tudo bem?", "hey", "hello", "salve",
            "quem é você?", "Quem é você", "quem e voce", "quem é a Thalita?", "qual é o seu nome?", "qual seu nome",
            "como você se chama?", "se apresente", "oi, quem é você?", "o que você faz?", "o que você pode fazer?",
            "como você pode me ajudar?", "você pode me ajudar?"})
    void cumprimentosEPerguntasSobreElaViramApresentacao(String msg) {
        assertThat(ThalitaPersona.isIntroductionRequest(msg)).as(msg).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "tudo bem com o estoque?", "oi, quanto vendemos hoje?", "bom dia, preciso repor o quê?",
            "qual o nome do produto mais vendido?", "qual é o nome do cliente da venda 12?", "o que você acha do dashboard de ontem?",
            "quem é o cliente que mais comprou?", "dia 10 vendemos quanto?", "tudo", "e", "bem",
            "oi oi oi oi oi oi oi oi"})
    void perguntasDeVerdadeSeguemParaAIA(String msg) {
        assertThat(ThalitaPersona.isIntroductionRequest(msg)).as(msg).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "SELLER", "STOCK_KEEPER"})
    void apresentacaoUsaOPrimeiroNomeESeIdentificaComoThalitaDaOrden(String role) {
        String text = ThalitaPersona.introduction("Douglas Vargas", role);
        assertThat(text).startsWith("Oi, Douglas!").contains("**Thalita**").contains("**Orden**").contains("Em que posso ajudar?");
    }

    @org.junit.jupiter.api.Test
    void apresentacaoSemNomeNaoQuebra() {
        assertThat(ThalitaPersona.introduction(null, "MANAGER")).startsWith("Oi! Eu sou a **Thalita**");
    }

    @org.junit.jupiter.api.Test
    void exemplosDependemDoPapel() {
        assertThat(ThalitaPersona.introduction("Ana", "SELLER")).contains("camiseta tamanho M").doesNotContain("repor com urgência");
        assertThat(ThalitaPersona.introduction("Ana", "STOCK_KEEPER")).contains("sem estoque").doesNotContain("Quanto vendemos");
        assertThat(ThalitaPersona.introduction("Ana", "MANAGER")).contains("Quanto vendemos");
    }

    @ParameterizedTest
    @ValueSource(strings = {"conta a história da Orden", "Qual a história do sistema?", "quem criou o sistema?",
            "Quem fez a Orden?", "como surgiu a Orden?", "qual a sua história, Thalita?", "quem desenvolveu você?"})
    void pedidosDaHistoriaViramAHistoriaPronta(String msg) {
        assertThat(ThalitaPersona.isStoryRequest(msg)).as(msg).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"história de vendas da loja", "qual a história de compras do cliente João?",
            "quem criou a venda 12?", "me dá uma receita de bolo", "oi", "histórico de estoque da camiseta"})
    void perguntasDeDadosNaoSaoAHistoria(String msg) {
        assertThat(ThalitaPersona.isStoryRequest(msg)).as(msg).isFalse();
    }

    @org.junit.jupiter.api.Test
    void historiaTemOSenaiETodaAEquipe() {
        String h = ThalitaPersona.story("Matheus Carvalho");
        assertThat(h).startsWith("Matheus, a Orden nasceu").contains("SENAI de Tubarão").contains("2026")
                .contains("Desenvolvimento de Sistemas").contains("estoque sem controle")
                .doesNotContain("joalheria").doesNotContain("vem de")
                .contains("Douglas Vargas").contains("Julia Alcantara").contains("Guilherme Dower")
                .contains("Vitor Nunes").contains("Vinicius Clemente").contains("Vinicius Muniz");
    }
}
