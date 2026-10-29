# Desenho — Previsão de vendas e metas pela previsão (crm/12)

## Peças

| Peça | O que faz |
| --- | --- |
| `forecast/RetailCalendar` | Feriados (nacionais, de SC e de Tubarão, e os móveis pela Páscoa) e datas comerciais com a rampa e o aumento padrão. Sem banco. O `seed/Feriados` usa o mesmo `isHoliday`. |
| `forecast/ForecastModel` | A conta inteira, pura (sem banco e sem relógio): aprende os fatores com o histórico diário e prevê qualquer dia. Testada sozinha (`ForecastModelTest`). |
| `forecast/ForecastService` | Lê o histórico diário (até 400 dias), monta períodos, gráfico e explicação, e calcula as sugestões de meta. |
| `forecast/ForecastController` | `GET /crm/forecast?months=` (gerente). |
| `TargetController` | `GET /crm/targets/suggestions?month=&growth=` (gerente). Aplicar usa o `PUT /crm/targets/{id}` que já existe. |
| Front | `ForecastPage` (`/previsao`), coluna e barra de sugestão na `TargetsPage`, item no menu. |
| Thalita | `previsao_vendas` e `sugestao_metas` (gerente). |

Sem tabela nova: a previsão é recalculada a cada pedido (a conta leva milissegundos com 400 dias).

## A conta (ForecastModel)

Entrada: faturamento e número de vendas de cada dia, de forma contínua, até **ontem**.

1. **Dias especiais**: feriado ou dentro da janela de uma data comercial. Eles ficam fora do aprendizado do dia da
   semana e do salário, para não inflar o "normal".
2. **Dia da semana** (últimos 84 dias, só dias normais): média de cada dia da semana ÷ média das 7 médias. Dia da
   semana com menos de 2 observações vale 1. A média dos 7 fatores é 1.
3. **Salário**: tirado o dia da semana, média dos dias 5–9 e 20–24 ÷ média dos outros dias normais. Precisa de 4
   dias de cada lado; limitado entre 0,8 e 1,4.
4. **Nível local** de um dia: média dos dias normais a até 14 dias dele, já sem dia da semana e salário.
5. **Feriado**: média de real ÷ (nível local × salário) nos feriados fora de datas comerciais. Com menos de 2,
   vale o fator do domingo.
6. **Datas comerciais**: para cada data cuja janela inteira está no histórico, razão = real ÷ esperado sem a data.
   Como a rampa sobe aos poucos, o pico aprendido = 1 + (razão − 1) ÷ média da rampa. Limitado entre 1 e 3.
7. **Mês do ano** (só com 365 dias): média do mês ÷ média do ano, já sem os outros fatores.
8. **Nível atual**: média dos últimos 28 dias, cada dia dividido por todos os seus fatores.
9. **Previsão do dia** = nível × (feriado ou dia da semana) × salário × data comercial × mês.
10. **Faixa provável**: σ = desvio do log(real ÷ ajustado) nos dias normais dos últimos 56. Para um período,
    total × (1 ± 1,28 × σ × √Σf² ÷ Σf), onde f é a previsão de cada dia.
11. **Acerto**: refaz a conta com o histórico até 28 dias atrás e compara as 4 semanas seguintes com o real.

## Sugestão de meta

`parte do vendedor = faturamento das vendas que ele abriu nos últimos 90 dias ÷ faturamento da loja inteira no
mesmo período`. A soma das metas fica abaixo da previsão quando parte da loja vende pelo canal online ou pelo
gerente, e ninguém fica com a meta de vendas que não passam por ele.

## Contrato

```
GET /crm/forecast?months=3
{ today, historyFrom, historyDays,
  model: { baselineRevenue, baselineSales, weekday:[{day, factor}], payday:{factor, learned},
           holiday:{factor, learned}, monthSeasonality:{active, factors|null},
           events:[{name, date, rampDays, uplift, learned}], accuracy:{weeklyErrorPercent, weeks}|null },
  periods: [{key, label, from, to, revenue, low, high, sales, realized}],
  days: [{date, actual, revenue, low, high, sales, holiday, events}] }

GET /crm/targets/suggestions?month=2026-11&growth=10
{ month, monthForecast, growthPercent, basisFrom, basisTo, storeRevenue,
  rows:[{sellerId, sellerName, revenue, sharePercent, currentTarget, suggested, reason}] }
```
