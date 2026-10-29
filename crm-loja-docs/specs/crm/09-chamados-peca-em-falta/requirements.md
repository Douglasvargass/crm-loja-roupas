# Requisitos — Chamados de peça em falta (crm/09)

> Épico: Estoque · Histórias: HU47, HU48, HU49 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 29/10/2026 · Data: 29/10/2026
> Depende de: crm/03-estoque, crm/04-vendas · Altera: a Talita deixa de ser só consulta (uma exceção: abrir chamado)
> Prioridade (MoSCoW): Should have · Tamanho: G (tabela nova, 6 regras, 2 telas e o sino, ferramenta que grava)
> Status: implementada em 29/10/2026

## Contexto

O cliente chega querendo uma peça que acabou (ou que a loja nem tem). Hoje o vendedor diz "não tem", e a venda se
perde sem deixar rastro: o estoque não sabe que havia procura e o cliente não é avisado quando a peça chega.

## Objetivo

Como **vendedor**, quero registrar a peça que o cliente queria e não tinha, para o estoque repor e eu avisar o
cliente quando chegar.

Como **estoquista ou gerente**, quero ver na hora o que os clientes procuram e não acham, para decidir o que repor.

## Requisitos

### REQ-01 — Abrir chamado

Quem atende (vendedor, gerente e a equipe técnica) abre o chamado com: o que o cliente queria (3 a 200 letras),
tamanho, cor, quantidade (1 a 99), produto do catálogo (opcional) e cliente (opcional).

- DADO uma peça que nem existe no catálogo, QUANDO o vendedor descreve "Bermuda de linho bege 42", ENTÃO o chamado é aberto sem produto.
- DADO o estoquista, QUANDO tenta abrir chamado, ENTÃO recebe 403: quem abre é quem atende o cliente.

### REQ-02 — Peça com estoque não abre chamado

- DADO um produto ativo com estoque suficiente, QUANDO alguém tenta abrir chamado dele, ENTÃO vê "O produto X tem N em estoque: dá para vender agora, sem chamado." (409 `product_in_stock`).

### REQ-03 — Aviso para o estoque e o gerente

- DADO um chamado aberto, QUANDO o estoquista ou o gerente olha o sino no topo, ENTÃO vê a quantidade de chamados abertos e os 5 mais recentes.

### REQ-04 — A peça chegou: o chamado se resolve sozinho

A entrada de estoque do produto resolve os chamados abertos dele, do mais antigo ao mais novo, enquanto a quantidade que entrou cobrir cada um.

- DADO chamados de 1, 2 e 5 unidades da mesma peça, QUANDO entram 3 unidades, ENTÃO os dois primeiros são resolvidos com a nota "A peça chegou: entrada de 3 un. em dd/mm/aaaa." e o terceiro continua aberto.

### REQ-05 — Quem vê o quê

- Estoquista, gerente e equipe técnica veem todos os chamados.
- DADO o vendedor A, QUANDO lista os chamados, ENTÃO **não** vê os do vendedor B; QUANDO tenta cancelar o de B, ENTÃO recebe "Chamado não encontrado" (404), sem saber que ele existe.

### REQ-06 — Resolver e cancelar, sempre com nota

- Resolver: estoquista, gerente e equipe técnica, com nota de 3 a 255 letras.
- Cancelar: os mesmos, ou o vendedor que abriu, com motivo.
- DADO um chamado já resolvido ou cancelado, QUANDO alguém tenta fechar de novo, ENTÃO recebe 409 `request_closed`.
- Nada se apaga: o chamado fica com quem fechou, quando e a nota; abrir, resolver e cancelar vão para a auditoria.

### REQ-07 — Aviso para quem abriu

- DADO que o chamado do vendedor foi resolvido ou cancelado, QUANDO ele olha o sino, ENTÃO vê a novidade com a nota; ao abrir o sino, a novidade conta como vista e o número zera.

### REQ-08 — A Talita abre chamado, só com confirmação

- DADO o vendedor dizendo "a cliente queria a jaqueta jeans G e acabou", QUANDO a Talita entende, ENTÃO mostra o resumo e pergunta "Posso abrir o chamado?"; só com o "sim" ela abre (ferramenta `abrir_chamado`, `confirmado=true`).
- DADO a Talita chamando a ferramenta sem confirmação, QUANDO executa, ENTÃO nada é gravado.
- É a **única** ação que a Talita faz; o chamado aberto por ela aparece como "(pela Talita)".

## Não inclui

Avisar o cliente por WhatsApp ou e-mail, pedido de compra ao fornecedor, prazo de entrega, reposição automática pelo agente de demonstração resolvendo chamados (ele não passa pela entrada de estoque).

## Riscos

- **A Talita grava algo:** limitado a criar chamado, que pode ser cancelado; exige confirmação; não aceita id de quem abre vindo do modelo.
- **Vendedor vendo chamado de colega:** a lista e o cancelamento usam o id da sessão; teste negativo em StockRequestIT.

## Métrica de sucesso

Na demonstração: o vendedor abre o chamado pela Talita, o estoquista vê no sino, registra a entrada da peça e o vendedor recebe o aviso, tudo em menos de 2 minutos.
