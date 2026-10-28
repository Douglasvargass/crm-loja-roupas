package br.senai.crmloja.core.http;

// Validação de campo → HTTP 400 (ex.: invalid_cpf).
public class InvalidInputException extends RuntimeException {
    private final String code;
    public InvalidInputException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
