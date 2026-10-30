package br.senai.crmloja.modules.crm.store;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.auth.AuthService;
import br.senai.crmloja.core.auth.RecoveryMailer;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.identity.AppUser;
import br.senai.crmloja.core.identity.AppUserRepository;
import br.senai.crmloja.core.rbac.ModuleRepository;
import br.senai.crmloja.core.rbac.ModuleRoleRepository;
import br.senai.crmloja.core.rbac.UserModuleAccess;
import br.senai.crmloja.core.rbac.UserModuleAccessRepository;
import br.senai.crmloja.modules.crm.customer.CustomerRepository;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Conta do cliente pelo site ("Sou cliente" → Criar conta) e "Esqueci minha senha" (RN65 a RN68).
// O cliente entra com o e-mail. A recuperação manda um link de uso único, válido por 30 minutos, e a resposta é
// sempre a mesma, exista ou não o e-mail (não dá para descobrir quem tem conta).
@Service
public class AccountService {

    static final int TOKEN_MINUTES = 30;
    static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Signup(String fullName, String cpf, String email, String phone, String zipCode, String street,
                         String number, String complement, String district, String city, String state,
                         String password, Boolean acceptTerms) {}

    private final AppUserRepository users;
    private final ModuleRepository modules;
    private final ModuleRoleRepository roles;
    private final UserModuleAccessRepository accesses;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final PasswordEncoder encoder;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final RecoveryMailer mailer;
    private final jakarta.validation.Validator validator;

    public AccountService(AppUserRepository users, ModuleRepository modules, ModuleRoleRepository roles,
                          UserModuleAccessRepository accesses, CustomerRepository customers,
                          CustomerService customerService, PasswordEncoder encoder, NamedParameterJdbcTemplate jdbc,
                          AuditService audit, RecoveryMailer mailer, jakarta.validation.Validator validator) {
        this.users = users;
        this.modules = modules;
        this.roles = roles;
        this.accesses = accesses;
        this.customers = customers;
        this.customerService = customerService;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.audit = audit;
        this.mailer = mailer;
        this.validator = validator;
    }

    // --------------------------------------------------------------------------------- criar conta (RN65, RN66)

    @Transactional
    public String signup(Signup s) {
        if (!Boolean.TRUE.equals(s.acceptTerms())) {
            throw new InvalidInputException("terms_required", "Para criar a conta, aceite o uso dos seus dados pela loja.");
        }
        String email = normalizeEmail(s.email());
        AuthService.validateNewPassword(s.password());
        if (users.findByLoginIgnoreCase(email).isPresent() || emailTaken(email)) {
            throw new BusinessRuleException("email_in_use", "Já existe uma conta com este e-mail. Entre ou use \"Esqueci minha senha\".");
        }
        String cpf = s.cpf() == null ? "" : s.cpf().replaceAll("\\D", "");
        Long customerId;
        var existing = customers.findByCpf(cpf);
        if (existing.isPresent()) {
            var c = existing.get();
            // RN66: o CPF já é cliente do balcão. Só vira conta se o e-mail for o mesmo do cadastro (prova de que é ele).
            if (c.getUserId() != null) {
                throw new BusinessRuleException("cpf_has_account", "Este CPF já tem conta. Entre com o seu e-mail ou recupere a senha.");
            }
            if (!email.equalsIgnoreCase(c.getEmail())) {
                throw new BusinessRuleException("cpf_registered",
                        "Este CPF já está cadastrado na loja com outro e-mail. Fale com a loja para atualizar o cadastro.");
            }
            customerId = c.getId();
        } else {
            var req = new CustomerRequest(s.fullName(), cpf, digits(s.phone()), email,
                    digits(s.zipCode()), s.street(), s.number(), s.complement(),
                    s.district(), s.city(), s.state() == null ? null : s.state().trim().toUpperCase(Locale.ROOT));
            // As mesmas regras do cadastro feito no balcão (nome, CPF válido, telefone, CEP, endereço).
            var violations = validator.validate(req);
            if (!violations.isEmpty()) {
                throw new InvalidInputException("invalid_signup", violations.iterator().next().getMessage());
            }
            customerId = customerService.create(req, null).id();
        }

        AppUser u = new AppUser(email, s.fullName() == null ? email : s.fullName().trim(), encoder.encode(s.password()));
        u.setMustChangePassword(false);
        users.saveAndFlush(u);
        jdbc.update("update app_user set email = :e where id = :id", Map.of("e", email, "id", u.getId()));
        var loja = modules.findByCode("loja").orElseThrow();
        var papel = roles.findByModuleIdAndCode(loja.getId(), "CUSTOMER").orElseThrow();
        accesses.save(new UserModuleAccess(u.getId(), loja.getId(), papel.getId()));
        jdbc.update("update customer set user_id = :u where id = :c and user_id is null", Map.of("u", u.getId(), "c", customerId));
        audit.record(u.getId(), "loja", "customer_signup", "customer", customerId, Map.of("email", email));
        return email;
    }

    // ------------------------------------------------------------------------- esqueci minha senha (RN67, RN68)

    // Sempre devolve sem erro: o e-mail existir ou não, a resposta da tela é a mesma.
    @Transactional
    public void forgotPassword(String rawEmail, String baseUrl) {
        String email;
        try {
            email = normalizeEmail(rawEmail);
        } catch (InvalidInputException e) {
            throw e;   // e-mail mal digitado: avisa (não revela se existe)
        }
        List<Map<String, Object>> found = jdbc.queryForList("""
                select u.id, u.full_name from app_user u
                 where u.is_active and not u.is_blocked
                   and (lower(u.email) = :e
                        or u.id = (select c.user_id from customer c where lower(c.email) = :e and c.user_id is not null limit 1))
                 limit 1""", Map.of("e", email));
        if (found.isEmpty()) return;
        Long userId = ((Number) found.get(0).get("id")).longValue();
        String name = (String) found.get(0).get("full_name");
        Integer recent = jdbc.queryForObject("""
                select count(*) from password_reset_token where user_id = :u and created_at > now() - interval '1 hour'""",
                Map.of("u", userId), Integer.class);
        if (recent != null && recent >= MAX_REQUESTS_PER_HOUR) return;

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("insert into password_reset_token (user_id, token_hash, expires_at) values (:u, :h, :x)",
                Map.of("u", userId, "h", sha256(token), "x", OffsetDateTime.now().plusMinutes(TOKEN_MINUTES)));
        audit.record(userId, "core", "password_reset_requested", "app_user", userId, Map.of());
        mailer.sendReset(email, firstName(name), baseUrl + "/redefinir-senha?token=" + token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) throw invalidToken();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, user_id from password_reset_token
                 where token_hash = :h and used_at is null and expires_at > now() for update""",
                Map.of("h", sha256(token.trim())));
        if (rows.isEmpty()) throw invalidToken();
        AuthService.validateNewPassword(newPassword);
        Long tokenId = ((Number) rows.get(0).get("id")).longValue();
        Long userId = ((Number) rows.get(0).get("user_id")).longValue();
        AppUser u = users.findById(userId).orElseThrow(this::invalidToken);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        // Link de uso único; os outros links pedidos antes também deixam de valer. Sessões abertas caem.
        jdbc.update("update password_reset_token set used_at = now() where user_id = :u and used_at is null", Map.of("u", userId));
        jdbc.update("update refresh_token set revoked_at = now() where user_id = :u and revoked_at is null", Map.of("u", userId));
        audit.record(userId, "core", "password_reset_done", "app_user", userId, Map.of("tokenId", tokenId));
    }

    private BusinessRuleException invalidToken() {
        return new BusinessRuleException("invalid_reset_link",
                "Este link de recuperação não vale mais (passou de 30 minutos ou já foi usado). Peça um novo.");
    }

    private boolean emailTaken(String email) {
        Integer n = jdbc.queryForObject("select count(*) from app_user where lower(email) = :e", Map.of("e", email), Integer.class);
        return n != null && n > 0;
    }

    static String normalizeEmail(String raw) {
        String e = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 120 || !EMAIL.matcher(e).matches()) {
            throw new InvalidInputException("invalid_email", "Informe um e-mail válido, ex.: nome@email.com.");
        }
        return e;
    }

    private static String digits(String v) {
        return v == null ? null : v.replaceAll("[^0-9]", "");
    }

    private static String firstName(String full) {
        if (full == null || full.isBlank()) return "cliente";
        return full.trim().split("\\s+")[0];
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
