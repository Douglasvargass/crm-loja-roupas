# 01-acesso · Design

## Back-end e front-end

- core/config (AppProperties fail-fast, SecurityConfig, CorsConfig)
- core/identity (AppUser, RefreshToken)
- core/auth (AuthController, AuthService, JwtService, AuthCookies, JwtCookieFilter)
- core/rbac (Module, ModuleRole, UserModuleAccess, RbacService)
- core/audit (AuditEvent, AuditService)
- core/seed/CoreSeeder, modules/crm/seed/CrmModuleSeeder
- V1__core_identity_rbac.sql, V2__core_audit.sql
- Front: data/http.ts, auth-events.ts, SessionContext, guards, LoginPage, ChangePasswordPage, UsersPage

## Testes

- AuthIT: login ok/errado, conta inativa, 401 sem cookie, 403 sem papel, troca de senha obrigatória, reuso de refresh (CT32–CT37)

## Decisões pendentes

- (preencher durante a implementação)
