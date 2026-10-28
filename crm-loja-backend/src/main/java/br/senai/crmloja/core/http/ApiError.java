package br.senai.crmloja.core.http;

import com.fasterxml.jackson.annotation.JsonInclude;

// Corpo de erro padrão: frase em português + código estável para a tela decidir a mensagem.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String code) {
    public static ApiError of(String error) { return new ApiError(error, null); }
}
