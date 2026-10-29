# Requisitos — Agente de vendas da demonstração (crm/08)

> Épico: Dados de demonstração · Histórias: HU46 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 29/10/2026 · Data: 29/10/2026
> Depende de: dados de demonstração (doc 14), crm/06 (metas) · Altera: —
> Prioridade (MoSCoW): Could have · Tamanho: M (1 tabela nova, 3 regras novas, 1 cartão de tela)
> Status: implementada em 29/10/2026

## Contexto

Os dados de demonstração são 3 meses de vendas criados uma vez, na primeira subida. Depois disso o sistema para
no tempo: o mês avança, o painel do vendedor fica parado e o dashboard de "Hoje" fica vazio até alguém vender à mão.

## Objetivo

Como **equipe**, quero que o sistema de demonstração continue vendendo sozinho, todo dia, de um jeito parecido com
a loja de verdade, para que a banca encontre dados vivos em qualquer dia que abrir o sistema.

## Requisitos

### REQ-01 — Um dia fechado por vez, sempre no passado

O agente fecha os dias inteiros que já passaram e ainda não têm vendas simuladas, até ontem. Hoje nunca é simulado.

- DADO que o histórico vai até 29/10 e hoje é 02/11, QUANDO o servidor acorda, ENTÃO o agente gera 30/10, 31/10 e 01/11, nessa ordem.
- DADO um dia já fechado, QUANDO o agente roda de novo, ENTÃO não gera nada em dobro (o banco tem um dia único por linha do diário).

### REQ-02 — O mesmo jeito de vender do histórico

As vendas do dia saem do mesmo motor do histórico (doc 14): horário do shopping, peso do dia da semana e do mês,
feriados, datas comerciais, quinzena, peso de cada vendedor (5 : 4 : 3), reposição de terça com o giro real dos
últimos 7 dias, ajustes de inventário. Clientes: os cadastrados e ativos (58% das vendas identificadas).

### REQ-03 — A IA só escolhe o "clima" do dia

Se houver provedor de IA configurado (o mesmo da Talita; grátis no Groq ou no Gemini), o agente manda à IA só números
agregados (vendas e faturamento dos últimos 14 dias, peças por categoria, dia da semana, feriado, data comercial) e
recebe 3 números e uma frase: fator de movimento, categoria em destaque e motivo.

- DADO que a IA devolveu fator 9, QUANDO o agente usa, ENTÃO vale 1,40 (faixa de 0,60 a 1,40); destaque, de 1,00 a 1,80.
- DADO uma resposta que não dá para ler, IA desligada ou sem cota, QUANDO o agente roda, ENTÃO o dia sai no padrão (fator 1,0), com o motivo dizendo isso.
- A IA **não** recebe nome, CPF, e-mail ou telefone de cliente e **não** grava nada no banco.

### REQ-04 — Nunca contradizer o que é real

- DADO que alguém usou o sistema (venda, cancelamento ou movimento de estoque) depois do dia a fechar, QUANDO o agente chega nele, ENTÃO o dia é **pulado** e registrado como "Pulado", porque inventar vendas no passado quebraria a sequência do saldo no histórico de estoque.
- DADO uma venda de verdade sendo confirmada enquanto o agente roda, QUANDO os dois disputam o estoque, ENTÃO um espera o outro (trava do catálogo) e o saldo fica certo.

### REQ-05 — Diário para o gerente

- DADO o gerente na demonstração, QUANDO abre o Dashboard, ENTÃO vê o cartão "Diário do agente de vendas" com os últimos 14 dias: data, IA / Padrão / Pulado, motivo, categoria em destaque, vendas criadas e faturamento.
- DADO um vendedor ou estoquista, QUANDO chama `/crm/demo-agent`, ENTÃO recebe 403.
- DADO o sistema fora da demonstração (sem perfil dev/demo), QUANDO o gerente abre o Dashboard, ENTÃO o cartão **não** aparece e nada é gerado.

### REQ-06 — Acordar todo dia, de graça

O agente roda quando o servidor sobe e de hora em hora enquanto está acordado. Um workflow do GitHub Actions
(`acordar-demo.yml`, grátis em repositório público) abre o site às 03:10 de São Paulo, quando ninguém está usando.

## Não inclui

Cancelamentos simulados nos dias novos (o histórico cancela 1 a 3 dias depois; o agente fecha um dia por vez),
clientes novos, produtos novos, simulação de "hoje" em tempo real.

## Riscos

- **Banco grátis do Render expira em 30 dias.** Quando for recriado, o histórico de 3 meses volta e o agente continua dali.
- **Cota grátis da IA:** com ela esgotada, os dias saem no padrão; nada quebra.

## Métrica de sucesso

Em qualquer dia da semana em que a banca abrir o sistema, o dashboard de "Este mês" e o Meu painel mostram vendas até ontem.
