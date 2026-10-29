# Requisitos — "Ver como" (crm/10)

> Épico: Acesso · História: HU50 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 29/10/2026 · Data: 29/10/2026
> Depende de: crm/01-acesso · Altera: — · Prioridade: Should have · Tamanho: M · Status: implementada em 29/10/2026
> Padrão trazido da Plataforma de Operações da Vialaser (impersonação só leitura).

## Objetivo

Como **gerente**, quero ver o sistema exatamente como um vendedor, estoquista ou Operações e Compras vê, para
conferir o que cada um enxerga e demonstrar à banca sem precisar da senha de ninguém.

## Requisitos

### REQ-01 — Quem usa e quem pode ser visto
Gerente, equipe técnica (Engenheiro de Software, Marketing e Desenvolvimento) e super admin. Pode ser vista qualquer
conta ativa com papel no sistema, menos a própria.

- DADO um vendedor, QUANDO tenta usar o "Ver como" (tela ou API), ENTÃO recebe 403.
- DADO o gerente, QUANDO escolhe a si mesmo ou um id que não existe, ENTÃO recebe "Esta pessoa não pode ser vista agora." (404).

### REQ-02 — Vê exatamente o que a pessoa vê
Menu, tela inicial, permissões e números passam a ser os da pessoa vista. O super admin perde o "passa em tudo" enquanto olha.

- DADO o gerente vendo como Vitor, QUANDO abre o sistema, ENTÃO cai em Meu painel com os números do Vitor e **não** consegue abrir o Dashboard (403), como o Vitor.

### REQ-03 — Só para olhar
- DADO o "Ver como" ligado, QUANDO qualquer alteração é tentada (vender, cadastrar, abrir chamado, perguntar à Thalita), ENTÃO recebe "Você está vendo como X: é só para olhar…" (403 `read_only_view_as`).
- Só passam: sair do "Ver como", sair do sistema e renovar a sessão.

### REQ-04 — Seguro
- Cookie próprio (`view_as`), httpOnly, assinado, vale 30 minutos e só para quem pediu: usado por outra pessoa, é ignorado.
- O token do "Ver como" nunca serve de login da pessoa vista (401).
- Entrar e sair ficam na auditoria (`view_as_started`, `view_as_ended`), com quem olhou e quem foi vista.

### REQ-06 — Loja do cliente (Prévia da loja, 29/10/2026)

O gerente quer ver o site como **qualquer** cliente vê, sem escolher um cliente. No "Ver como", a primeira opção é
"Loja do cliente": abre `/previa-loja`, o mesmo layout do espaço do cliente, com a vitrine de verdade.

- O carrinho fica só na tela; nada é reservado, pago ou gravado; o botão Pagar fica travado com o motivo.
- Meus pedidos e Meus dados mostram só exemplos: nenhum dado de cliente aparece.
- DADO o gerente, QUANDO lê `/loja/products`, ENTÃO recebe a vitrine; QUANDO lê `/loja/cart` ou `/loja/orders`, ENTÃO recebe 403.
- DADO o vendedor ou o estoquista, QUANDO lê `/loja/products`, ENTÃO recebe 403.

### REQ-05 — Sempre claro na tela
- Faixa fixa no topo: "Vendo como **Vitor Nunes** (Vendedor) · só para olhar" e o botão "Voltar a ser Guilherme".
- Sair do sistema também sai do "Ver como".

## Testes
`ViewAsIT` (5): ver como vendedor e só leitura com auditoria; só quem vale por gerente; não vê a si mesmo; cookie de outra pessoa ignorado; token não vira login.
