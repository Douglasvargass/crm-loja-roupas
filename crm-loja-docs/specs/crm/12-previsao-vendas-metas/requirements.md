# Requisitos — Previsão de vendas e metas pela previsão (crm/12)

> Épico: Vendas · Histórias: HU58, HU59 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 30/10/2026 ("previsão de vendas por períodos e sazonalidade, assim como aplicado no estoque; as metas dos vendedores serem feitas por esses dados") · Data: 30/10/2026
> Depende de: crm/04-vendas, crm/05-dashboard, crm/06-painel-vendedor-metas
> Prioridade (MoSCoW): Should have · Tamanho: M (sem tabela nova, 6 regras, 1 tela nova, 1 tela alterada, 2 ferramentas da Thalita)
> Status: implementada em 30/10/2026

## Contexto

A Reposição já prevê quando cada peça acaba, mas pela média simples dos últimos 30 dias. Para vendas, o gerente
não tinha previsão nenhuma: a meta de cada vendedor era um número digitado "de cabeça", e a projeção do Meu painel
só repete o ritmo do mês. A loja tem movimento muito diferente por dia da semana (sábado vende mais que o dobro da
segunda), sobe na semana do salário, muda nos feriados e cresce nas datas comerciais (Dia das Mães, Black Friday,
Natal). Uma média simples erra justamente nesses dias.

## Objetivo

Como **gerente**, quero ver quanto a loja deve vender nos próximos dias e meses, já contando dia da semana,
salário, feriados e datas comerciais, para planejar equipe, compras e metas.

Como **gerente**, quero que o sistema sugira a meta de cada vendedor a partir da previsão, para a meta ser justa e
alcançável, e aplicar a sugestão com um clique.

## Requisitos

### REQ-01 — Previsão por período (HU58)

A tela **Previsão de vendas** mostra o faturamento e o número de vendas previstos para: próximos 7 dias, próximos
30 dias, fechamento do mês atual (o que já vendeu até ontem + a previsão de hoje até o fim do mês) e cada um dos
próximos meses (1 a 12; padrão 3). Cada valor vem com a **faixa provável** (cerca de 80% de chance de cair dentro).

- A conta usa só vendas confirmadas, pela data da confirmação, no fuso da loja (mesmo critério do dashboard), dos
  dois canais (loja física e online) (RN55).
- O dia de hoje entra pela previsão, não pelo parcial do dia (RN55).
- Gráfico: os últimos 28 dias realizados e os próximos 60 dias previstos, com feriados e datas comerciais marcados.
- DADO menos de 14 dias com venda no histórico, QUANDO a tela abre, ENTÃO aparece "Ainda não há vendas suficientes
  para prever: são precisos pelo menos 14 dias com venda." (409 `not_enough_history`), sem números inventados.
- DADO um vendedor ou estoquista, QUANDO tenta abrir, ENTÃO recebe 403 (e o menu nem mostra).
- DADO meses fora de 1 a 12, QUANDO pede a previsão, ENTÃO recebe 400 `invalid_months`.

### REQ-02 — Sazonalidade explicada (HU58)

A previsão de cada dia = **nível atual** × **dia da semana** (ou **feriado**) × **semana do salário** ×
**data comercial** × **mês do ano** (RN56). A tela mostra cada fator, em palavras, e de onde ele veio:

- **Nível atual:** média dos últimos 28 dias, já sem o efeito dos outros fatores (RN56).
- **Dia da semana:** aprendido com as últimas 12 semanas, sem contar feriados e datas comerciais (RN57).
- **Semana do salário** (dias 5 a 9 e 20 a 24): aprendida comparando esses dias com os outros (RN57).
- **Feriado:** aprendido com os feriados do histórico (pelo menos 2); sem isso, vale como domingo, porque o
  shopping abre em horário de domingo (RN57).
- **Datas comerciais** (Dia das Mães, Namorados, Pais, Cliente, Crianças, Black Friday e Natal): o movimento sobe
  aos poucos nos dias antes da data. Quando o histórico tem a janela inteira da data, o aumento é **aprendido**;
  senão usa a **estimativa padrão do varejo**, marcada como tal (RN58).
- **Mês do ano:** só com pelo menos 1 ano de histórico. Antes disso, a tela diz que o mês do ano ainda não entra (RN58).
- **Acerto:** com pelo menos 56 dias de histórico, a tela mostra o erro médio por semana da previsão nas últimas 4
  semanas, refeita como se fosse 28 dias atrás (RN59).
- DADO um histórico em que sábado vende o dobro da segunda, QUANDO a previsão é feita, ENTÃO o sábado previsto é
  cerca do dobro da segunda.
- DADO um histórico sem a Black Friday, QUANDO a previsão cobre novembro, ENTÃO a semana da Black Friday sobe pela
  estimativa padrão, e a tela mostra "estimativa padrão".

### REQ-03 — Meta sugerida pela previsão (HU59)

Na tela **Metas**, para o mês escolhido (atual ou até 12 à frente), cada vendedor ganha uma **sugestão**:

- Sugestão = previsão do mês da loja × parte do vendedor no faturamento da loja nos últimos 90 dias × (1 +
  crescimento escolhido), arredondada para a centena (RN60).
- Crescimento: de −20% a +50%, padrão 0% (RN60). Fora disso: 400 `invalid_growth`.
- Vendedor sem venda nos últimos 90 dias não recebe sugestão: "Sem vendas nos últimos 90 dias: defina a meta à
  mão." (RN60).
- "Usar" aplica a sugestão de uma pessoa; "Aplicar a todos" aplica todas depois de confirmar. As duas passam pela
  mesma regra e auditoria da meta digitada (crm/06, REQ-01 a REQ-04).
- DADO dois vendedores com 60% e 30% do faturamento da loja e a previsão do mês em R$ 100.000, QUANDO o gerente pede
  a sugestão com 10% de crescimento, ENTÃO recebe R$ 66.000 e R$ 33.000.
- DADO um mês que já fechou, QUANDO pede sugestão, ENTÃO recebe 409 `month_closed` (meta de mês fechado não muda).
- DADO um vendedor, QUANDO pede a sugestão, ENTÃO recebe 403.

### REQ-04 — Thalita (HU58, HU59)

A Thalita do gerente responde "quanto vamos vender em novembro?" e "qual meta você sugere para o Vitor?" com as
ferramentas `previsao_vendas` e `sugestao_metas`, que devolvem exatamente a conta da tela. Ela não grava metas:
orienta a aplicar na tela Metas.

## Não inclui

- Previsão por produto ou categoria (a Reposição continua com a média de 30 dias por peça).
- Previsão por vendedor no Meu painel (a projeção dele continua sendo o ritmo do mês).
- Clima, promoções e campanhas como fator.
- Gravar o histórico das previsões para comparar depois.
