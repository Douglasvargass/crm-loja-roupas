package br.senai.crmloja.modules.crm.store;

// Acesso dos testes ao hash do link de recuperação (o mesmo que o AccountService grava).
public final class AccountServiceAccess {

    private AccountServiceAccess() {}

    public static String sha256(String raw) {
        return AccountService.sha256(raw);
    }
}
