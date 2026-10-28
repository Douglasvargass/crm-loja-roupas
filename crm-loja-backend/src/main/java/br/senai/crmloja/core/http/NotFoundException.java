package br.senai.crmloja.core.http;

// Registro inexistente → HTTP 404.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
