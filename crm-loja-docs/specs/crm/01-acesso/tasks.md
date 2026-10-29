# 01-acesso · Tasks

- [x] core/config (AppProperties fail-fast, SecurityConfig, CorsConfig)
- [x] core/identity (AppUser, RefreshToken)
- [x] core/auth (AuthController, AuthService, JwtService, AuthCookies, JwtCookieFilter)
- [x] core/rbac (Module, ModuleRole, UserModuleAccess, RbacService)
- [x] core/audit (AuditEvent, AuditService)
- [x] core/seed/CoreSeeder, modules/crm/seed/CrmModuleSeeder
- [x] V1__core_identity_rbac.sql, V2__core_audit.sql
- [x] Front: data/http.ts, auth-events.ts, SessionContext, guards, LoginPage, ChangePasswordPage, UsersPage
- [x] Testes: AuthIT: login ok/errado, conta inativa, 401 sem cookie, 403 sem papel, troca de senha obrigatória, reuso de refresh (CT32–CT37)
- [x] Atualizar specs/api-contract.md se rota, DTO ou papel mudou
- [x] Portão back-end: ./mvnw verify
- [x] Portão front-end: pnpm lint && pnpm typecheck && pnpm build
- [x] PR para develop com CI verde
- [x] Resumo em docs/implementacoes/01-acesso.md
