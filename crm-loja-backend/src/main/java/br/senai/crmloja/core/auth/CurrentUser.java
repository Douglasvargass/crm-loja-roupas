package br.senai.crmloja.core.auth;

// Principal de cada requisição. role = papel no módulo crm (MANAGER, SELLER, STOCK_KEEPER) ou null.
public record CurrentUser(Long id, String login, String fullName, boolean superAdmin,
                          boolean mustChangePassword, String role) {
}
