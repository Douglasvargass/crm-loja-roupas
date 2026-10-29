# Processo de requisitos

Como um pedido novo vira código no Orden. É a versão enxuta do processo "Criação de Requisitos" usado na
Plataforma de Operações da Vialaser, adaptada para uma equipe de 6 pessoas: copiamos a **forma de escrever** e
deixamos de fora a parte de atender pedidos de gente leiga por e-mail.

As specs `crm/06` (painel do vendedor e metas, implementada) e `crm/07` (espaço do cliente, pronta para
implementar) são os primeiros exemplos completos.

## Os 5 passos

| # | Passo | O que sai | Quem |
| --- | --- | --- | --- |
| 1 | **Entender** | 3 respostas: o que acontece hoje, quem sente, o que acontece se nunca for feito. Prioridade MoSCoW. Busca rápida de pedido parecido nas issues e em `specs/` | Quem pediu + 1 pessoa da equipe |
| 2 | **Formalizar** | História, critérios DADO/QUANDO/ENTÃO (com o negativo), regras R1..Rn, fora de escopo, papéis | A mesma pessoa |
| 3 | **Revisar** | Outra pessoa aplica o checklist abaixo e aprova ou aponta o que falta | Revisor diferente de quem escreveu |
| 4 | **Registrar** | Issue de História (pelo modelo), etiquetas, tamanho, tarefas por pessoa; `requirements.md` em `specs/crm/NN-nome/` por PR | Quem escreveu |
| 5 | **Validar na demo** | Roteiro de jornadas (tabela ID / Caso / Passos / Esperado / Status), que vira parte do doc 12 | Quem implementou + revisor |

Depois do passo 4, quem implementa escreve o `design.md` e o `tasks.md` da mesma pasta, e eles são revisados no PR
**antes** de codar.

## Como escrever

**História:** `Como [papel], quero [ação], para [benefício].`

**Critério de aceite**, sempre com pelo menos um negativo (quem **não** vê, o que **não** acontece):

```
- DADO que [contexto], QUANDO [ação], ENTÃO [resultado].
- DADO um vendedor, QUANDO procura o total da loja, ENTÃO não encontra.
```

**Regra de negócio, uma por linha.** Um "e" ligando duas obrigações são duas regras. Na spec, R1..Rn viram
REQ-01..NN na mesma ordem, e a numeração não muda depois de publicada.

```
R1 — Regra: [uma frase, uma regra só]
     Exemplo: DADO [..], QUANDO [..], ENTÃO [..].
     Exceções: [nenhuma / lista]
     Quem decidiu: [nome, dd/mm/aaaa]
```

**Nunca deduzir.** O que ninguém souber vira uma linha em "Decisões pendentes", com uma proposta padrão e para
quem perguntar, e a issue ganha a etiqueta `a-confirmar`.

## Modelo do `requirements.md`

```markdown
# Requisitos — [título] (crm/NN)
> Épico: [..] · Histórias: [HU..] · Solicitante: [nome] · Origem: [conversa / reunião dd/mm] · Data: [dd/mm/aaaa]
> Depende de: [specs ou "—"] · Altera: [regra ou tela que muda, ou "—"]
> Prioridade (MoSCoW): [..] · Tamanho: [P/M/G] · Status: [em elaboração / pronta para implementar / implementada]
## Contexto
## O problema
## Objetivo
## Requisitos
### REQ-01 — [nome curto]
[regra em prosa direta]
- DADO [..], QUANDO [..], ENTÃO [..].
## Não inclui
## Abrangência        (papéis que veem e que não veem)
## Riscos, dados sensíveis e dependências
## Métrica de sucesso (como saberemos que funcionou)
## Decisões pendentes antes de implementar
| # | Pergunta | Proposta padrão | Para quem |
```

Seção que não se aplica é apagada; nunca "N/A". O índice `specs/README.md` mostra o status de cada spec.

## Prioridade e tamanho

| MoSCoW | Quando | Etiqueta |
| --- | --- | --- |
| Must | sem isso o enunciado não fecha, ou algo para de funcionar | `prioridade:alta` |
| Should | importante, mas há contorno | `prioridade:media` |
| Could | extra, depois do essencial | `prioridade:baixa` |
| Won't | registrado, fora desta rodada | issue fechada como "not planned" |

| Tamanho | Critério (vale o mais alto atingido; na dúvida, o maior) |
| --- | --- |
| P | sem regra nova, sem tabela nova, ajuste em 1 rota ou 1 tela |
| M | 1 ou 2 regras novas, ou 1 tabela nova, ou 1 a 3 telas |
| G | 3 ou mais regras, fluxo novo, várias tabelas ou mais de um módulo |

Tamanho é esforço, não prazo.

## Checklist do revisor (passo 3)

- [ ] O problema está escrito sem a solução embutida.
- [ ] Nenhuma palavra vaga: "rápido", "fácil", "logo", "todo mundo", "intuitivo".
- [ ] Todo critério é testável e há pelo menos um negativo de acesso.
- [ ] Cada regra é uma só, com exemplo.
- [ ] "Não inclui" existe e é concreto.
- [ ] Papéis que veem e que não veem estão listados.
- [ ] Buscou pedido parecido (issues abertas e fechadas, `specs/`).
- [ ] Dados de exemplo são fictícios (CPF de teste, nada de pessoa real).
- [ ] Se muda regra já decidida, está escrito em "Altera:".
- [ ] Toca acesso, preço/total, baixa de estoque ou migration destrutiva? Então a issue leva `revisao-dupla`.

## Etiquetas do GitHub (18)

```
tipo:epico  tipo:historia  tipo:erro  tipo:tarefa
modulo:clientes  modulo:produtos  modulo:estoque  modulo:vendas  modulo:acesso  modulo:loja-online
camada:backend  camada:frontend
prioridade:alta  prioridade:media  prioridade:baixa
tamanho:p  tamanho:m  tamanho:g
bug  a-confirmar  revisao-dupla
```

Para criar de uma vez no repositório (quem tem permissão de escrita):

```bash
for l in tipo:epico tipo:historia tipo:erro tipo:tarefa modulo:clientes modulo:produtos modulo:estoque \
         modulo:vendas modulo:acesso modulo:loja-online camada:backend camada:frontend prioridade:alta \
         prioridade:media prioridade:baixa tamanho:p tamanho:m tamanho:g bug a-confirmar revisao-dupla; do
  gh label create "$l" -R Douglasvargass/crm-loja-roupas --force
done
```

**`revisao-dupla`:** PR que toca autenticação ou permissão, cálculo de preço ou total, baixa de estoque, ou
migration destrutiva precisa de **2 aprovações**. O critério é o assunto que a mudança toca, não a pasta.

## Modelos de issue

Em `.github/ISSUE_TEMPLATE/`: **Épico** (uma por módulo, com as jornadas ponta a ponta e o roteiro de validação),
**História** (as seções acima) e **Erro** (esperado × aconteceu, passo a passo, mensagem literal). O GitHub mostra
os três ao clicar em "New issue".

## Relato de erro

Sete perguntas: o que esperava, o que aconteceu (texto literal do erro), sempre ou às vezes, passo a passo, desde
quando, quantas pessoas, existe contorno. Mais: papel de quem viu e onde (local, Render). Nunca pedir senha.
Todo erro corrigido ganha um teste que falhava antes (CONTRIBUTING.md).

## O que ficou de fora do processo original, e por quê

| Do processo da Vialaser | Por que não aqui |
| --- | --- |
| Modo conversa por e-mail, pacote do pedido, caixa de entrada | Não há solicitante externo: o pedido nasce na equipe ou com o professor |
| 5 agentes (duplicidade, descoberta, dados, revisor, verificador) | O revisor é uma pessoa; os modelos de issue já garantem a estrutura |
| Campos de issue da organização, tipos de issue | Não existem em repositório pessoal; as etiquetas fazem o papel |
| Horas calibradas, homologação de 3 h fixas | P/M/G basta para ordenar o trabalho |
| "Não separar back-end e front-end na mesma História" | Com 6 pessoas em paralelo, a História é dividida em Tarefas por pessoa ou camada |

## Glossário curto

| Termo | Significa |
| --- | --- |
| Vendedor(a) da venda | Quem abriu a venda (`sale.created_by`) |
| Venda confirmada | Status `CONFIRMED`: baixou o estoque e conta no faturamento |
| Meta | Valor em R$ por vendedor e mês (spec 06) |
| Dia útil | Segunda a sábado |
| Canal | Loja física (balcão) ou Online (spec 07) |
| Cliente | Cadastro em Clientes; com conta de acesso, entra no espaço do cliente (spec 07) |
