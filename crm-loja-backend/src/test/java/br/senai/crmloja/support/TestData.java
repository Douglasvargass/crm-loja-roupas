package br.senai.crmloja.support;

import java.util.concurrent.atomic.AtomicInteger;

// Gera CPFs válidos e códigos únicos, para cada teste ter dados próprios no banco compartilhado.
public final class TestData {

    private static final AtomicInteger SEQ = new AtomicInteger((int) (System.nanoTime() % 100_000));

    private TestData() {}

    public static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    public static String cpf() {
        int n = SEQ.incrementAndGet();
        String base = String.format("%09d", 100_000_000 + (n * 7919L) % 899_999_999);
        int d1 = dv(base, 10);
        int d2 = dv(base + d1, 11);
        return base + d1 + d2;
    }

    private static int dv(String digits, int weight) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) sum += (digits.charAt(i) - '0') * (weight - i);
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    public static String customerJson(String name, String cpf) {
        return """
            {"fullName":"%s","cpf":"%s","phone":"(48) 99123-4567","email":"cliente@email.com",
             "zipCode":"88010-000","street":"Rua Felipe Schmidt","number":"10","complement":null,
             "district":"Centro","city":"Florianópolis","state":"SC"}""".formatted(name, cpf);
    }

    public static String productJson(String code, long categoryId, String price, int initialStock) {
        return """
            {"code":"%s","name":"Produto %s","description":"Teste","categoryId":%d,"size":"M","color":"Azul",
             "price":%s,"initialStock":%d,"minStock":2}""".formatted(code, code, categoryId, price, initialStock);
    }
}
