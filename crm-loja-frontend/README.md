# crm-loja-frontend

SPA em React 19 + Vite + TypeScript estrito + TanStack Query 5 + Tailwind 4.

```bash
pnpm install        # sem pnpm instalado: npx pnpm install
pnpm dev            # http://localhost:5173, com proxy /api → back-end em :8080
```

Back-end em outra porta: `VITE_API_TARGET=http://localhost:8081 pnpm dev`.

Antes de entregar: `pnpm lint && pnpm typecheck && pnpm build`.

## Estrutura (`presentation → data → domain`, alias `@/` = `src/`)

```
src/
├── app/            router, providers, SessionContext, ToastContext, guards
├── data/           ÚNICA fronteira com a API: http.ts, dto.ts, adapters.ts, hooks.ts, queryKeys.ts
├── domain/         permissions.ts (o que cada papel vê)
├── lib/            formatação e máscaras, ViaCEP, debounce
└── presentation/
    ├── components/ atoms · molecules · organisms · templates
    ├── pages/      uma pasta/arquivo por tela
    └── styles/     tokens.css (paleta clara e escura), global.css
```

- Tokens só em cookie httpOnly; em 401 o `http.ts` renova a sessão uma vez (deduplicado entre abas) e repete a chamada.
- Toda mutação de venda, estoque ou produto invalida dashboard, produtos e alertas: os indicadores se atualizam sozinhos.
- O CEP do cliente preenche o endereço pelo ViaCEP; se o serviço não responder, digita-se à mão.
