package br.senai.crmloja.core.text;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.util.Locale;

// Busca que ignora maiúsculas e acentos ("joao" acha "João") sem depender da extensão unaccent,
// que exige superusuário no Postgres. translate() troca as letras acentuadas pelas simples.
public final class TextSearch {

    private static final String FROM = "áàâãäéèêëíìîïóòôõöúùûüçñ";
    private static final String TO   = "aaaaaeeeeiiiiooooouuuucn";

    private TextSearch() {}

    public static String fold(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).trim();
    }

    public static Predicate containsFolded(CriteriaBuilder cb, Expression<String> column, String term) {
        Expression<String> folded = cb.function("translate", String.class,
                cb.lower(column), cb.literal(FROM), cb.literal(TO));
        return cb.like(folded, "%" + escapeLike(fold(term)) + "%", '\\');
    }

    public static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
