package br.senai.crmloja.core.users;

import java.security.SecureRandom;

public final class PasswordGenerator {

    // Sem 0/O e 1/l/I: a senha provisória é ditada ou copiada à mão.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {}

    public static String generate(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
