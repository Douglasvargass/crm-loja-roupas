package br.senai.crmloja.core.http;

// Sem sessão válida → HTTP 401.
public class UnauthorizedException extends RuntimeException {
    private final String code;
    public UnauthorizedException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
