# Requisitos — Espaço do cliente: vitrine, carrinho, pagamento e meus pedidos (crm/07)

> Épico: Vendas online · Histórias: HU40 a HU45 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 29/10/2026 · Data: 29/10/2026
> Depende de: crm/04-vendas (a venda e a baixa de estoque são as mesmas), crm/01-acesso (login e troca de senha)
> Altera: dashboard (filtro de canal), modelo de dados (canal da venda, pagamento, conta do cliente)
> Prioridade (MoSCoW): Could have · Tamanho: G (fluxo novo, várias entidades, módulo novo de acesso)
> Status: implementada em 29/10/2026, com as 4 propostas padrão das decisões abaixo

## Contexto

O enunciado deixa pagamento e carrinho fora do MVP, mas aceita extras depois que o essencial estiver pronto, e
ele está. A banca são os professores Vitor Freitas e Matheus Carvalho: a ideia é que eles entrem como **clientes**
e comprem sozinhos, do login até "Meus pedidos", enquanto a equipe mostra o pedido chegando no dashboard.

## O problema

- **Quem sente:** o cliente, que hoje só compra no balcão; e a loja, que não vende fora do horário.
- **Se nunca for feito:** o sistema continua só interno, e a demonstração depende de alguém da equipe operar.

## Objetivo

Como **cliente**, quero ver os produtos disponíveis, montar um carrinho, pagar e acompanhar os meus pedidos,
para comprar sem ir à loja.

## Requisitos

### REQ-01 — Conta de cliente separada da equipe

O cliente entra pelo mesmo login, mas a conta dele não tem papel no módulo `crm`: tem o papel `CUSTOMER` num
módulo próprio, `loja`. As rotas dele ficam em `/loja/**`; as da equipe (`/crm/**`, `/admin/**`) devolvem 403.

- DADO um cliente logado, QUANDO abre o sistema, ENTÃO cai na **Vitrine**, num layout próprio, sem o menu da equipe.
- DADO um cliente, QUANDO digita `/clientes`, `/estoque` ou `/` na barra de endereço, ENTÃO volta para a Vitrine; QUANDO chama `/crm/...` direto, ENTÃO recebe 403.
- DADO alguém da equipe, QUANDO abre `/loja/...`, ENTÃO não vê carrinho de ninguém (a equipe usa as telas de venda).

### REQ-02 — Primeiro acesso com senha padrão e troca obrigatória

- DADO uma conta de cliente nova, QUANDO entra com a senha `cliente12345`, ENTÃO a única tela disponível é **Trocar senha**.
- DADO a tela de troca, QUANDO a senha nova tem menos de 8 caracteres ou é igual à atual, ENTÃO é recusada com a frase do erro.
- DADO a senha trocada, QUANDO o cliente entra de novo, ENTÃO `cliente12345` não funciona mais.
- DADO o cliente, QUANDO quiser, ENTÃO troca a senha de novo pelo link **Trocar senha** no topo.

### REQ-03 — A conta é ligada ao cadastro do cliente

Cada conta de cliente aponta para exatamente um cadastro em Clientes (nome, CPF, endereço). O histórico de
compras do cadastro (inclusive as do balcão) é o "Meus pedidos" dele.

### REQ-04 — Vitrine

Mostra só produto **ativo com estoque**, com foto padrão da categoria, nome, tamanho, cor e preço. Busca por nome e
filtros por categoria e tamanho.

- DADO um produto sem estoque ou inativo, QUANDO o cliente procura, ENTÃO ele **não** aparece.
- DADO um produto com estoque até o mínimo, QUANDO aparece, ENTÃO mostra "Últimas unidades", sem a quantidade exata.
- DADO o cliente, QUANDO vê um produto, ENTÃO **não** vê estoque exato, estoque mínimo, código interno de movimentação nem quem cadastrou.

### REQ-05 — Carrinho

O carrinho é uma venda aberta (`DRAFT`), de canal `ONLINE`, com o cliente já vinculado. Um carrinho por cliente.

- DADO o cliente, QUANDO adiciona um produto, ENTÃO o preço é copiado naquele momento e o total é calculado no servidor (mesma regra RN12 e RN13 do balcão).
- DADO o mesmo produto de novo, QUANDO adiciona, ENTÃO soma na mesma linha.
- DADO uma quantidade acima do estoque, QUANDO adiciona, ENTÃO vê o aviso na hora, e nada muda.
- DADO o carrinho, QUANDO o cliente sai e entra de novo, ENTÃO os itens continuam lá.
- O carrinho **não reserva** estoque, igual ao balcão.

### REQ-06 — Pagamento simulado

Formas: **Pix** e **cartão de crédito**, simuladas. Nenhum dado real de cartão é guardado: só os 4 últimos dígitos.

- DADO o cliente no pagamento, QUANDO escolhe Pix e confirma, ENTÃO o pagamento é aprovado na hora.
- DADO cartão, QUANDO o número termina em `0002`, ENTÃO o pagamento é **recusado** com "Pagamento não autorizado. Tente outro cartão ou Pix.", e nada muda no estoque (serve para demonstrar o erro).
- DADO qualquer outro número, QUANDO confirma, ENTÃO o pagamento é aprovado.

### REQ-07 — Fechar o pedido é tudo ou nada

Pagamento aprovado confirma a venda pelo mesmo caminho do balcão (`SaleService.confirm`): baixa o estoque de todos
os itens, ou de nenhum.

- DADO que um item acabou enquanto o cliente pagava, QUANDO confirma, ENTÃO nada é cobrado, nada é baixado, e a mensagem diz qual produto faltou.
- DADO o pedido aprovado, QUANDO termina, ENTÃO vê "Pedido nº X confirmado" e o carrinho fica vazio.
- DADO o mesmo pedido, QUANDO o cliente clica duas vezes em pagar, ENTÃO só um pagamento e uma baixa acontecem.

### REQ-08 — Meus pedidos

- DADO o cliente, QUANDO abre Meus pedidos, ENTÃO vê os pedidos dele (online e balcão) com número, data, total, forma de pagamento e situação.
- DADO o número de um pedido de **outro** cliente, QUANDO o cliente tenta abrir, ENTÃO recebe "Pedido não encontrado" (404), sem revelar que ele existe.

### REQ-09 — Meus dados

- DADO o cliente, QUANDO edita telefone, e-mail ou endereço, ENTÃO o cadastro em Clientes é atualizado, com as mesmas validações do balcão.
- DADO o cliente, QUANDO procura mudar nome ou CPF, ENTÃO **não** encontra: só a loja muda (evita trocar a identidade de quem comprou).

### REQ-10 — A loja enxerga o canal

- DADO o gerente no dashboard, QUANDO escolhe "Canal: Online", ENTÃO cartões e gráficos mostram só pedidos online.
- DADO um pedido online pago, QUANDO o gerente abre Vendas, ENTÃO o pedido aparece com o selo "Online" e a forma de pagamento.
- Pedido online **não** conta para a meta de nenhum vendedor (spec 06).
- O gerente cancela pedido online pela regra atual (RN18, RN19): devolve o estoque e marca o pagamento como estornado.

### REQ-12 — Talita do cliente e pedido de peça (HU57, 29/10/2026)

- A Talita aparece no espaço do cliente (`/loja/assistant`), com instruções e ferramentas só do cliente: vitrine (sem estoque exato), categorias, meus pedidos, detalhe do meu pedido, minhas solicitações e pedir peça. Nenhuma ferramenta da equipe; a equipe não usa as do cliente.
- Peça que não está na vitrine: o cliente pede pela Talita (com confirmação do resumo) ou pelo botão **"Não achou? Peça à loja"** da vitrine. Vira um chamado de peça em falta (spec crm/09) já ligado ao cliente: o gerente e o estoque veem no sino, e o cliente acompanha em **Meus pedidos → Peças que pedi à loja** (Aguardando a loja, Atendido, Cancelado, com a nota da loja).
- DADO o cliente A, QUANDO lista as solicitações, ENTÃO vê só as dele; o cliente B não vê as de A.
- Na Prévia da loja da equipe, a Talita não aparece e o pedido de peça fica travado com o motivo.

### REQ-11 — Contas de demonstração dos professores

| Login | Pessoa | Senha no primeiro acesso |
| --- | --- | --- |
| `vitor.freitas` | Vitor Freitas | `cliente12345` (troca obrigatória) |
| `matheus.carvalho` | Matheus Carvalho | `cliente12345` (troca obrigatória) |

Cada um com um cadastro em Clientes (CPF de teste válido, não real). Criadas pelo seed de demonstração; se o
professor já trocou a senha, o seed não mexe nela.

## Não inclui

Frete, cupom, vários endereços, auto-cadastro pela internet, cancelamento pelo próprio cliente, devolução ou troca,
nota fiscal, pagamento real (gateway), avaliação de produto, lista de desejos.

## Abrangência

Papéis: Cliente (novo, módulo `loja`). Gerente e técnicos veem os pedidos online nas telas atuais. Vendedor e
estoquista veem o pedido online como uma venda comum.

## Riscos, dados sensíveis e dependências

- **Alguém veria o que não devia?** Risco principal: cliente ver dado de outro cliente (CPF, endereço, pedidos). Mitigação: toda rota `/loja/**` resolve o cliente pela sessão, nunca por parâmetro; testes negativos de acesso para cada rota.
- **Senha conhecida:** `cliente12345` está na documentação; a troca no primeiro acesso é obrigatória (REQ-02).
- **Cartão:** nunca guardar número completo nem CVV; só os 4 últimos dígitos.
- **Estoque:** o fechamento usa a mesma confirmação com trava de linha do balcão; venda online e de balcão disputando a última peça → uma passa, a outra recebe "estoque insuficiente".

## Métrica de sucesso

- Na apresentação, cada professor compra sozinho, do login ao "Meus pedidos", em menos de 3 minutos.
- Nenhum teste de acesso cruzado (cliente → dado de outro cliente ou da equipe) passa.

## Proposta técnica (para o design.md)

- `V7`: `sale.channel` (`STORE` padrão, `ONLINE`); `customer.user_id` único e opcional (liga conta e cadastro);
  tabela `payment` (`sale_id` único, `method` PIX/CARD, `status` APPROVED/DECLINED/REFUNDED, `amount`,
  `card_last4`, `created_at`), imutável por trigger exceto a passagem para REFUNDED.
- Módulo `loja` com papel `CUSTOMER` semeado; `JwtCookieFilter` passa a ler o papel do módulo da rota.
- Front: layout `StoreShell` (sem menu da equipe), páginas Vitrine, Produto, Carrinho, Pagamento, Meus pedidos, Meus dados.

## Decisões (tomadas em 29/10/2026: valeram as propostas padrão)

| # | Pergunta | Proposta padrão | Para quem |
| --- | --- | --- | --- |
| 1 | Se o preço do produto mudar com o item no carrinho, vale o preço antigo ou o novo? | O novo: o carrinho atualiza e avisa antes de pagar | Equipe |
| 2 | Cliente pode se cadastrar sozinho pela internet? | Não neste MVP: só contas criadas pela loja | Equipe |
| 3 | O cartão de recusa (`0002`) fica visível na tela para a demonstração? | Sim, numa dica "cartão de teste" | Equipe |
| 4 | Pedido online pago e cancelado pelo gerente: pagamento estornado automaticamente? | Sim, status REFUNDED, sem dinheiro real | Equipe |
