# 13-acessibilidade · Design

| Peça | Onde |
| --- | --- |
| Estado e regras | `src/lib/appearance.ts`: guarda em `localStorage` (`orden.aparencia`), aplica em `<html data-theme data-contrast data-font data-motion>` e acompanha o modo do computador no "Automático" |
| Antes de a tela aparecer | script no `index.html` (mesma regra) para não piscar claro e depois escuro |
| Cores | `presentation/styles/tokens.css`: o `:root` oficial não muda (só ganhou `--border-input` e `--on-error`); acrescenta escuro, claro+contraste e escuro+contraste, e variantes do `.cores-dashboard` (Ice) para cada modo; `.chrome-light` só no claro |
| Variante `dark:` | `@custom-variant dark` em `global.css` (vale pelo `data-theme`, não só pelo modo do computador) |
| Tamanho da letra | `html[data-font]` muda a base (112,5% e 125%); tudo em rem acompanha |
| Movimento | `html[data-motion="reduce"]` zera animação e transição |
| Botão e janela | `organisms/AppearanceMenu.tsx` (AppShell, StoreShell e LoginPage) |
| Libras | `organisms/VLibras.tsx`: script fixo 7.12.2, marcações `vw`, montado em `app/providers.tsx` |
| Avisos | `app/context/ToastContext.tsx`: 8 s / 12 s, pausa, botão fechar |
| Teclado | atalho "Pular para o conteúdo" e `main#conteudo` em AppShell e StoreShell; foco visível em `@layer base` |
| Logo | `atoms/Logo.tsx`: placa `--logo-plate` sempre (transparente no claro, clara no escuro) |
| Conferência | `docs/qa/verificar-contraste.py` (pares de cores, inclusive os do Ice) |

**Por que no navegador e não na conta:** o botão precisa funcionar também na tela de login e para o cliente da loja, e não
exige nenhuma mudança no banco ou no back-end. A conta guardaria a escolha entre computadores; fica como evolução.

**Testes:** o front não tem testes automáticos; a conferência é `pnpm lint`, `typecheck`, `build`, o script de contraste e as
varreduras no navegador descritas no REQ-02 e REQ-04 (roteiro, seção 22).
