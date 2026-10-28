# Design e linguagem


- **Um cliente HTTP só** (`data/http.ts`): `credentials: 'include'`; em 401 tenta renovar a sessão **uma** vez e repete a chamada; a renovação é deduplicada (promessa compartilhada na aba + reserva de 30 s no `localStorage` entre abas), porque dois refresh em paralelo disparam a detecção de reuso e derrubam a sessão. Renovação proativa a cada 8 min.
- **Sessão sem token em storage**: o `SessionContext` chama `/auth/me` ao abrir e decide entre carregando, logado e anônimo.
- **TanStack Query** com `retry: false`, `staleTime: 30_000`, `refetchOnWindowFocus: false`. Toda mutação de venda, estoque ou produto **invalida** `dashboard`, `products` e `stockAlerts`, e os indicadores se atualizam sozinhos.
- **QueryState** em toda seção com dados: esqueleto no carregamento, erro com botão Tentar de novo, vazio com frase explicando.
- **Tradução em um lugar só**: `adapters.ts` converte `DRAFT` → "Aberta", `SALE_CANCELLATION` → "Estorno de venda", datas e moeda.
- **Botão travado** enquanto a requisição roda, contra clique duplo em Confirmar.
- **Máscaras**: CPF `000.000.000-00`, telefone `(00) 00000-0000`, CEP `00000-000`, moeda `R$ 0,00`; a API recebe só dígitos.
- **Tema** claro e escuro por tokens em `tokens.css` (`--bg`, `--surface`, `--text`, `--primary`, `--warning`, `--error`…) ligados ao Tailwind por `@theme inline`. A paleta é da loja: num projeto novo só os valores do `tokens.css` mudam.
- **Componentes** em atomic design: Button, Input, Modal, Badge, Skeleton, HelpHint · KpiCard, SearchInput, Pagination, QueryState, EmptyState, ErrorState · DataTable, Sidebar, Topbar · AppShell, ListPageTemplate.

## Linguagem simples na tela

| Em vez de | Escreva |
| --- | --- |
| "Status: DRAFT" | "Venda aberta" |
| "Erro 409" | "Estoque insuficiente para Calça Jeans 40: tem 2, você pediu 5." |
| "Registro excluído" | "Pronto! O cliente foi removido." |
| "Campo obrigatório" | "Informe o CPF do cliente." |
| "Inativar entidade" | "Este produto já tem vendas. Quer desativá-lo em vez de excluir?" |
