# Requisitos — Meu painel do vendedor e metas de venda (crm/06)

> Épico: Vendas · Histórias: HU37, HU38, HU39 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 29/10/2026 · Data: 29/10/2026
> Depende de: crm/04-vendas, crm/05-dashboard (filtro por vendedor) · Altera: tela inicial do vendedor (antes: Vendas)
> Prioridade (MoSCoW): Should have · Tamanho: G (tabela nova, 4 regras novas, 2 telas, rota com papel existente)
> Status: implementada em 29/10/2026

## Contexto

Hoje o vendedor entra direto na lista de vendas e não sabe quanto já vendeu no mês, nem se vai bater uma meta,
porque meta não existe no sistema. O gerente só vê o total da loja e, desde 29/10, filtra o dashboard por
vendedor, mas não tem onde registrar a meta de cada um. Na Vialaser isso é feito pelo "Raio-X de Vendas":
cada vendedora recebe só os próprios números, com meta, ritmo e quanto falta, sem ranking entre elas.

## O problema

- **Quem sente:** vendedores (não enxergam o próprio resultado) e gerente (acompanha meta em planilha fora do sistema).
- **Frequência:** todo dia de loja aberta.
- **Contorno hoje:** perguntar ao gerente, que soma à mão.
- **Se nunca for feito:** o vendedor só descobre se bateu a meta no fim do mês, quando não dá mais para reagir.

## Objetivo

Como **vendedor**, quero ver as minhas vendas do mês, a minha meta e quanto falta por dia, para saber se estou no
ritmo e reagir a tempo.

Como **gerente**, quero definir a meta de cada vendedor por mês e ver a equipe toda num quadro, para acompanhar
quem precisa de ajuda.

## Requisitos

### REQ-01 — Meta por vendedor e por mês

A meta é um valor em reais, maior que zero, por vendedor e por mês. Cada vendedor tem no máximo uma meta por mês.

- DADO que o gerente digita R$ 15.000,00 para Vitor Nunes em novembro/2026, QUANDO salva, ENTÃO o quadro mostra a meta de Vitor em novembro.
- DADO que Vitor já tem meta em novembro, QUANDO o gerente salva outro valor, ENTÃO o valor é trocado, sem criar segunda meta.
- DADO um valor zero, negativo ou com mais de 2 casas decimais, QUANDO o gerente salva, ENTÃO vê o erro ao lado do campo e nada é gravado.

### REQ-02 — Só o mês atual e os próximos 12 meses aceitam meta

Mês que já terminou não muda: a meta dele fica como estava, para o histórico não mudar.

- DADO que hoje é 29/10/2026, QUANDO o gerente tenta mudar a meta de setembro/2026, ENTÃO vê "Esse mês já fechou: a meta dele não muda mais." (409 `month_closed`).
- DADO um mês a mais de 12 meses à frente, QUANDO o gerente salva, ENTÃO é recusado (409 `month_too_far`).

### REQ-03 — Só vendedor ativo recebe meta

- DADO uma conta de estoquista, gerente ou inativa, QUANDO alguém tenta definir meta para ela, ENTÃO é recusado (409 `not_a_seller`).

### REQ-04 — Toda mudança de meta fica registrada

- DADO que a meta de Vitor era R$ 10.000,00, QUANDO o gerente muda para R$ 12.000,00, ENTÃO a auditoria grava quem mudou, quando, o mês, o valor anterior e o novo (`sales_target_set`).
- DADO que o gerente salva o mesmo valor que já estava, QUANDO confirma, ENTÃO nada novo é registrado.

### REQ-05 — O que conta como "vendido"

Conta a venda **confirmada** que o vendedor **abriu**, pela data de confirmação no fuso da loja. É a mesma regra do filtro "Vendedor(a)" do dashboard.

- DADO que Vitor abriu e confirmou uma venda de R$ 300,00 hoje, QUANDO abre o painel, ENTÃO o vendido do mês aumenta R$ 300,00.
- DADO que essa venda é cancelada, QUANDO o painel recarrega, ENTÃO o vendido volta e a venda aparece em "Canceladas", que não contam para a meta.

### REQ-06 — Dia útil é de segunda a sábado

Domingo não conta como dia útil, mas a venda feita no domingo entra no vendido. Feriado não é descontado, porque o shopping abre.

- DADO outubro/2026 (31 dias, 4 domingos), QUANDO o painel calcula, ENTÃO o mês tem 27 dias úteis.

### REQ-07 — Ritmo, projeção e quanto falta por dia

- **Média por dia útil** = vendido ÷ dias úteis de 1º até hoje.
- **Projeção** = média por dia útil × dias úteis do mês. Mês fechado: projeção = vendido.
- **Falta** = meta − vendido, nunca abaixo de zero.
- **Por dia útil daqui para a frente** = falta ÷ dias úteis depois de hoje. No último dia útil, é tudo o que falta.
- DADO 10/10/2026 (9 dias úteis passados, 18 restantes), meta R$ 2.700,00 e vendido R$ 900,00, QUANDO o painel calcula, ENTÃO mostra R$ 100,00 por dia útil, projeção R$ 2.700,00, falta R$ 1.800,00 e R$ 100,00 por dia útil daqui para a frente.

### REQ-08 — Situação da meta

| Situação | Quando |
| --- | --- |
| Meta batida | vendido ≥ meta |
| No ritmo | projeção ≥ meta |
| Atenção | projeção entre 80% e 100% da meta |
| Abaixo do ritmo | projeção abaixo de 80% da meta |
| Não bateu | mês fechado e vendido < meta |
| Sem meta | o gerente ainda não definiu |
| Ainda não começou | nenhum dia útil passou no mês |

### REQ-09 — Meu painel: cada vendedor vê só o que é dele

O painel mostra meta, vendido, % da meta, frases de quanto falta, média, projeção, vendas de hoje, vendas por dia, os produtos que ele mais vendeu e as últimas vendas que ele abriu.

- DADO Vitor logado, QUANDO abre o sistema, ENTÃO cai direto em **Meu painel**.
- DADO que Vinicius vendeu R$ 500,00, QUANDO Vitor abre o painel dele, ENTÃO esse valor **não** aparece para Vitor.
- DADO Vitor logado, QUANDO procura o total da loja, o quadro da equipe ou o painel de outro vendedor, ENTÃO **não** encontra: a API não tem parâmetro para pedir o painel de outra pessoa, e `/crm/targets` e `/crm/dashboard` devolvem 403.
- DADO o gerente, QUANDO chama `/crm/my-panel`, ENTÃO recebe 403: o painel é de quem vende.

### REQ-10 — Sem meta, o painel continua útil

- DADO que o gerente ainda não definiu a meta de novembro, QUANDO Vitor abre o painel, ENTÃO vê "Sua meta deste mês ainda não foi definida. Fale com o gerente." e, mesmo assim, o vendido, a média e a projeção.

### REQ-11 — Quadro de metas do gerente

Tela **Metas**: escolhe o mês, vê cada vendedor ativo em ordem alfabética (sem ranking) com meta, vendido, % da meta, projeção, falta e situação, e o total da equipe. Define ou corrige a meta na própria linha.

- DADO o gerente em Metas, QUANDO troca o mês para um mês fechado, ENTÃO os campos viram texto e aparece o aviso de mês fechado.
- DADO um vendedor, QUANDO tenta abrir Metas pela URL, ENTÃO volta para Meu painel.

### REQ-12 — A Thalita responde sobre metas

- DADO Vitor, QUANDO pergunta "quanto falta para a minha meta?", ENTÃO a Thalita responde com os números dele (ferramenta `meu_painel`).
- DADO Vitor, QUANDO pergunta quanto o Vinicius vendeu, ENTÃO a Thalita diz que o perfil dele não tem acesso.
- DADO o gerente, QUANDO pergunta "quem está mais longe da meta?", ENTÃO a Thalita usa o quadro (`metas_vendedores`).

## Não inclui

- Meta por loja, por categoria ou por produto; comissão; ranking entre vendedores.
- E-mail diário automático (como o Raio-X da Vialaser): fica para depois.
- Pedido online (spec 07) contando para meta de vendedor.

## Abrangência

Papéis: Vendedor(a) vê Meu painel. Gerente, Engenheiro(a) de Software e Marketing e Desenvolvimento veem e editam Metas. Estoquista não vê nenhum dos dois.

## Riscos, dados sensíveis e dependências

- **Se der errado, alguém veria o que não devia?** Sim: vendedor vendo número de colega. Mitigado pelo REQ-09 e coberto por teste (TargetIT).
- **Histórico:** meta de mês fechado não muda (REQ-02) e toda mudança vai para a auditoria (REQ-04).
- **Depende de:** o vendedor ser quem abriu a venda (`sale.created_by`). Venda aberta por um e confirmada por outro conta para quem abriu.

## Métrica de sucesso

- **Como saberemos que funcionou:** na demonstração, um vendedor responde sozinho "quanto falta para a meta e quanto preciso por dia" em menos de 10 segundos, pelo painel ou pela Thalita.

## Decisões pendentes antes de implementar

Nenhuma. As três dúvidas foram resolvidas assim: dia útil igual ao Raio-X (segunda a sábado); venda conta para quem abriu; mês fechado não muda.
