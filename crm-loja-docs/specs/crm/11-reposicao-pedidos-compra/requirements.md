# Requisitos — Reposição e pedidos de compra (crm/11)

> Épico: Estoque · Histórias: HU51, HU52, HU53, HU54 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 29/10/2026 ("o Vinicius Clemente poderia ter um dash do estoque: projeção do que pode faltar, pedidos a chegar") · Data: 29/10/2026
> Depende de: crm/03-estoque, crm/04-vendas, crm/09-chamados-peca-em-falta
> Prioridade (MoSCoW): Should have · Tamanho: G (2 tabelas, 6 regras, 2 telas, 2 ferramentas da Talita)
> Status: implementada em 29/10/2026

## Contexto

Vinicius Clemente cuida de Operações e Compras: recebe os chamados de peça em falta e faz o pedido ao fornecedor.
Até aqui ele só sabia que uma peça faltava **depois** que ela zerava, e o pedido ao fornecedor acontecia fora do
sistema. A loja não sabia o que estava a caminho nem quando chegava.

## Objetivo

Como **Operações e Compras**, quero ver o que vai faltar antes de faltar e quanto pedir, para comprar na hora certa.

Como **Operações e Compras**, quero registrar o que pedi ao fornecedor e quando chega, para a loja inteira saber o que está a caminho.

Como **estoquista ou gerente**, quero dar entrada no pedido que chegou com um clique, para o estoque subir e os chamados da peça fecharem sozinhos.

## Requisitos

### REQ-01 — Painel "O que vai faltar" (HU51)

Para cada peça ativa, o painel mostra: estoque e mínimo, quanto vende por dia, em quantos dias acaba (e a data), o
que está a caminho e quando chega, chamados abertos da peça, quanto pedir e a situação.

- Média por dia = unidades vendidas (vendas confirmadas, pela data da confirmação) nos últimos 30 dias ÷ 30 (RN43).
- Dias até acabar = estoque ÷ média, arredondado para baixo. Sem venda nos 30 dias, não há previsão (RN43).
- Quanto pedir = o que deve vender nos dias escolhidos (7 a 90; padrão 30) + estoque mínimo + chamados abertos da peça − estoque − o que já está a caminho; nunca negativo (RN44, RN45).
- Situação: **Zerado**; **Acaba em até 7 dias** (ou está no mínimo); **Acaba em até 21 dias**; **Tranquilo**; **Parado** (tem estoque e não vendeu em 30 dias); **Novo, sem histórico** (cadastrada há menos de 30 dias e sem venda: cedo para julgar, não conta como dinheiro parado) (RN46).
- Cartões no topo: zerados, acabam em 7 dias, para pedir, pedidos atrasados, chegam em 7 dias, dinheiro parado (estoque parado × preço de venda).
- DADO uma peça com 5 em estoque, mínimo 2 e 15 vendidas em 30 dias, QUANDO o painel abre com 30 dias, ENTÃO mostra 0,5 por dia, acaba em 10 dias, situação "Acaba em até 21 dias" e pedir 12.
- DADO um pedido de compra aberto de 12 dessa peça, QUANDO o painel recarrega, ENTÃO pedir vira 0 e a peça mostra "já resolve".
- DADO um pedido que chega depois da data em que a peça acaba, QUANDO o painel abre, ENTÃO a peça mostra "chega depois de acabar".
- DADO o vendedor, QUANDO tenta abrir o painel, ENTÃO recebe 403 (e o menu nem mostra).

### REQ-02 — Registrar pedido de compra (HU52)

Quem compra (Operações e Compras, gerente e a equipe técnica) registra: fornecedor (2 a 80 letras), data prevista
(de hoje até 180 dias), observação opcional e de 1 a 30 peças diferentes, cada uma de 1 a 9999 unidades.

- DADO a Reposição com peças marcadas, QUANDO clica em "Pedir marcadas" (ou "Pedir" numa linha), ENTÃO abre o novo pedido já com as peças e as quantidades sugeridas.
- DADO uma data no passado, QUANDO tenta registrar, ENTÃO vê "A data prevista não pode ser antes de hoje." (400 `date_in_past`).
- DADO a mesma peça em duas linhas, QUANDO tenta registrar, ENTÃO vê "junte numa linha só" (400 `duplicated_product`).
- DADO o estoquista, QUANDO tenta registrar, ENTÃO recebe 403: ele recebe a mercadoria, não compra.
- Registrar não envia nada ao fornecedor: serve para a loja saber o que está a caminho.

### REQ-03 — Receber o pedido (HU53)

- DADO um pedido aberto, QUANDO o estoquista, Operações e Compras ou o gerente clica em Receber e confirma, ENTÃO cada peça entra no estoque (movimento de Entrada com o motivo "Pedido de compra nº N · fornecedor"), o pedido fica Recebido e os chamados abertos das peças que a entrada cobre fecham sozinhos (RN35), com o aviso para quem abriu.
- Tudo ou nada: se uma entrada falhar, nada entra e o pedido continua aberto.
- DADO um pedido já recebido ou cancelado, QUANDO alguém tenta receber, ENTÃO recebe 409 `order_closed`.
- Chegou diferente do pedido? Recebe e corrige a peça com um Ajuste de estoque, com motivo (recebimento parcial fica fora desta versão).

### REQ-04 — Mudar a data e cancelar, sempre com motivo (HU54)

- Quem compra muda a data prevista (hoje a 180 dias, diferente da atual) com motivo de 3 a 255 letras; a data antiga e a nova vão para a auditoria e o pedido mostra "data mudou N×" (RN47).
- Quem compra cancela com motivo; o pedido não some, fica na aba Cancelados e deixa de contar como "a caminho" (RN47).
- Pedido aberto com a data prevista vencida aparece como **Atrasado**, no filtro próprio, no cartão da Reposição e no aviso de pendências ao entrar (RN48).

### REQ-05 — Talita

- `previsao_estoque` (gerente, estoque, Operações e Compras, equipe técnica): resumo e peças que pedem atenção, ou uma peça só; a mesma conta da tela.
- `pedidos_compra` (os mesmos): lista por situação ou só os atrasados.
- A Talita **não** registra nem recebe pedido de compra: explica onde fazer na tela.

### REQ-06 — Etapas do pedido e nota fiscal (HU55, 29/10/2026, V11)

O pedido passa por **pedido feito → em trânsito → em conferência → entrada feita** (RN49).

- Em trânsito: quem compra marca que saiu do fornecedor e pode já anotar o nº da nota fiscal.
- Em conferência: quem recebe marca que chegou na loja; a partir daí a data não muda mais (409 `wrong_step`).
- Entrada feita: depois de conferir, com o **nº da nota fiscal obrigatório** (400 `invoice_required`); a nota vai para o motivo da movimentação de estoque.
- Pular etapas é possível (a entrada vale a partir de qualquer etapa aberta); cancelar vale até a entrada.

### REQ-08 — Conferência: devolver a nota ou nota de bonificação (regra do Douglas, 29/10/2026, V12)

Na entrada, o que chegou é contado **peça por peça** (RN52). Nunca se dá entrada "mais ou menos" para ajustar depois.

- DADO que uma peça chegou a menos, QUANDO tenta dar entrada, ENTÃO recebe 409 `short_delivery` e a tela orienta a **devolver a nota**: a mercadoria volta ao fornecedor, nada entra, o pedido volta a "pedido feito" (datas de envio e chegada e a nota saem; `returnCount` sobe; motivo e contagem na auditoria).
- DADO que uma peça chegou a mais, QUANDO dá entrada sem a nota de bonificação, ENTÃO recebe 400 `bonus_invoice_required`; com ela, o pedido entra pela nota fiscal e o extra entra por uma segunda movimentação, "Bonificação · NF X".
- DADO que tudo bateu, QUANDO dá entrada, ENTÃO entrada normal; a quantidade conferida fica guardada em cada peça.
- Devolver a nota: quem recebe a mercadoria (G E), em conferência ou em trânsito.

### REQ-08 — Conferência: devolver a nota ou nota de bonificação (regra do Douglas, 29/10/2026, V12)

Na entrada, o que chegou é contado **peça por peça** (RN52). Nunca se dá entrada "mais ou menos" para ajustar depois.

- DADO que uma peça chegou a menos, QUANDO tenta dar entrada, ENTÃO recebe 409 `short_delivery` e a tela orienta a **devolver a nota**: a mercadoria volta ao fornecedor, nada entra, o pedido volta a "pedido feito" (datas de envio e chegada e a nota saem; `returnCount` sobe; motivo e contagem na auditoria).
- DADO que uma peça chegou a mais, QUANDO dá entrada sem a nota de bonificação, ENTÃO recebe 400 `bonus_invoice_required`; com ela, o pedido entra pela nota fiscal e o extra entra por uma segunda movimentação, "Bonificação · NF X".
- DADO que tudo bateu, QUANDO dá entrada, ENTÃO entrada normal; a quantidade conferida fica guardada em cada peça.
- Devolver a nota: quem recebe a mercadoria (G E), em conferência ou em trânsito.

### REQ-08 — Conferência: devolver a nota ou nota de bonificação (regra do Douglas, 29/10/2026, V12)

Na entrada, o que chegou é contado **peça por peça** (RN52). Nunca se dá entrada "mais ou menos" para ajustar depois.

- DADO que uma peça chegou a menos, QUANDO tenta dar entrada, ENTÃO recebe 409 `short_delivery` e a tela orienta a **devolver a nota**: a mercadoria volta ao fornecedor, nada entra, o pedido volta a "pedido feito" (datas de envio e chegada e a nota saem; `returnCount` sobe; motivo e contagem na auditoria).
- DADO que uma peça chegou a mais, QUANDO dá entrada sem a nota de bonificação, ENTÃO recebe 400 `bonus_invoice_required`; com ela, o pedido entra pela nota fiscal e o extra entra por uma segunda movimentação, "Bonificação · NF X".
- DADO que tudo bateu, QUANDO dá entrada, ENTÃO entrada normal; a quantidade conferida fica guardada em cada peça.
- Devolver a nota: quem recebe a mercadoria (G E), em conferência ou em trânsito.
- Cada etapa vai para a auditoria. Em trânsito e em conferência ainda contam como "a caminho" na Reposição.

### REQ-07 — Estoque no Dashboard (HU56, 29/10/2026)

- O Dashboard ganha o bloco **Estoque**: peças e valor em estoque, zerados, acabam em 7 dias, para pedir, chamados abertos, dinheiro parado, pedidos feitos, em trânsito, em conferência e notas que deram entrada (7 dias), mais as listas Acabam primeiro, Mais saem, Pedidos feitos, Em trânsito, Em conferência e Notas que deram entrada.
- Cartão **Sugestão de pedido**: as peças que precisam de compra, com a quantidade sugerida, e o botão que abre o pedido pronto (quem compra).
- O gerente vê vendas + estoque. Operações e Compras e o estoquista abrem o Dashboard e veem **só** o estoque (faturamento continua só do gerente). Operações e Compras entra pelo Dashboard.

## Não inclui

Cadastro de fornecedores, preço de custo e valor do pedido, envio do pedido ao fornecedor
(e-mail ou WhatsApp), sazonalidade na previsão (a média é simples, dos últimos 30 dias), reposição simulada do agente
de demonstração passando pelos pedidos.

## Riscos

- A média simples não enxerga datas especiais (Black Friday, Natal): em mês atípico, aumente os dias a cobrir.
- Peça que acabou há muito tempo fica sem venda no período e a média cai: ela aparece como Zerada, e o mínimo + chamados ainda geram sugestão.
