package br.senai.crmloja.modules.crm.customer;

// Validação do CPF pelos dois dígitos verificadores (módulo 11).
public final class Cpf {

    private Cpf() {}

    public static boolean isValid(String digits) {
        if (digits == null || !digits.matches("\\d{11}")) return false;
        // 000.000.000-00, 111.111.111-11... passam no cálculo, mas não são CPFs reais.
        if (digits.chars().distinct().count() == 1) return false;
        return digit(digits, 9) == digits.charAt(9) - '0' && digit(digits, 10) == digits.charAt(10) - '0';
    }

    private static int digit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }
}
