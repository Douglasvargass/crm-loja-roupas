package br.senai.crmloja.core.http;

// Quebra de regra de negócio → HTTP 409 com code estável (ex.: insufficient_stock).
public class BusinessRuleException extends RuntimeException {
    private final String code;
    public BusinessRuleException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
