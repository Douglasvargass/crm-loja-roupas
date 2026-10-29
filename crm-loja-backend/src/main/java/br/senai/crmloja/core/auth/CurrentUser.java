package br.senai.crmloja.core.auth;

// Principal de cada requisição. role = papel no módulo crm (MANAGER, SELLER, STOCK_KEEPER...) ou null.
// viewerId/viewerName: preenchidos só no "Ver como" (spec crm/10). Aí id, nome e papel são da pessoa vista, e quem
// está olhando de verdade é o viewer; tudo é só leitura (JwtCookieFilter barra qualquer alteração).
public record CurrentUser(Long id, String login, String fullName, boolean superAdmin,
                          boolean mustChangePassword, String role, Long viewerId, String viewerName) {

    public CurrentUser(Long id, String login, String fullName, boolean superAdmin, boolean mustChangePassword,
                       String role) {
        this(id, login, fullName, superAdmin, mustChangePassword, role, null, null);
    }

    public boolean viewingAs() {
        return viewerId != null;
    }
}
