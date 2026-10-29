## 14. Dados de demonstração: 3 meses de operação

Este documento explica de onde vêm os números que o sistema mostra. O banco de demonstração não é uma amostra
aleatória: é uma simulação de **89 dias de operação de uma loja de roupas em shopping**, calibrada com o horário
real do Farol Shopping (Tubarão/SC), o calendário de feriados da cidade e os indicadores publicados do varejo de
moda brasileiro.

O objetivo é que o dashboard, os relatórios e o assistente de IA respondam sobre uma operação que **se comporta
como uma loja de verdade** — com sábado cheio, segunda fraca, pico no Dia dos Pais e estoque que precisa ser
reposto toda semana.

> Implementação: `crm-loja-backend/src/main/java/br/senai/crmloja/modules/crm/seed/HistoricoVendasSeeder.java`
> e `Feriados.java`. Perfis `dev` e `demo`, `@Order(40)` — roda depois do `DevDataSeeder`.

---

### 14.1 A loja modelada

| Parâmetro | Valor | Origem |
| --- | --- | --- |
| Praça | Farol Shopping, Tubarão/SC | shopping regional de porte médio |
| Janela simulada | últimos 89 dias (≈ 3 meses) | relativa à data de execução |
| Catálogo | 12 produtos em 5 categorias | o mesmo do `DevDataSeeder` |
| Faixa de preço | R$ 29,90 a R$ 299,90 | média R$ 136,57 |

A janela é **relativa a hoje**, não fixa no calendário. Isso garante que o dashboard — cujo período padrão é o
mês corrente — sempre tenha dados, independentemente de quando o projeto for executado pela banca.

---

### 14.2 Horário de funcionamento

Esta é a variável que mais molda a curva de vendas, e é a que o enunciado exigiu reproduzir fielmente.

| Dia | Lojas | Observação |
| --- | --- | --- |
| Segunda a sábado | **10h às 22h** | 12 horas de operação |
| Domingo e feriados | **14h às 21h** | 7 horas — 42% menos tempo de loja aberta |

Fonte: [farolshopping.com.br](https://farolshopping.com.br/).

A consequência prática é que **feriado não é "um dia a mais de semana"**: roda em horário de domingo. Por isso o
seeder decide a janela do dia pela função `Feriados.ehFeriado(data)`, e não pelo dia da semana.

---

### 14.3 Calendário de feriados

Os feriados são calculados **por regra**, não por lista fixa de datas, para o seeder continuar correto em
qualquer ano. Cobrem os nacionais, o estadual de Santa Catarina e o municipal de Tubarão:

| Data | Feriado | Esfera |
| --- | --- | --- |
| 1º/01 | Confraternização Universal | nacional |
| móvel | Carnaval (Páscoa − 47) | nacional |
| móvel | Sexta-feira Santa (Páscoa − 2) | nacional |
| 21/04 | Tiradentes | nacional |
| 1º/05 | Dia do Trabalho | nacional |
| móvel | Corpus Christi (Páscoa + 60) | nacional |
| **11/08** | **Dia do Estado de Santa Catarina** | **estadual** |
| 07/09 | Independência do Brasil | nacional |
| **15/09** | **Nossa Senhora da Piedade** | **municipal de Tubarão** |
| 12/10 | Nossa Senhora Aparecida | nacional |
| 02/11 | Finados | nacional |
| 15/11 | Proclamação da República | nacional |
| 20/11 | Consciência Negra | nacional |
| 25/12 | Natal | nacional |

Fonte dos municipais e estaduais: [calendário oficial da Prefeitura de Tubarão](https://tubarao.sc.gov.br/divulgado-calendario-de-feriados-municipais-e-pontos-facultativos-para-o-ano-de-2026/),
Decreto nº 8.948. As datas móveis usam o algoritmo de Meeus/Jones/Butcher para a Páscoa.

Na janela de agosto a outubro de 2026 isso resulta em **4 feriados** — 11/08 (terça), 07/09 (segunda), 15/09
(terça) e 12/10 (segunda) — todos operando em horário reduzido.

---

### 14.4 O modelo de demanda

O número de vendas de cada dia é o produto de cinco fatores, mais um ruído aleatório de ±18%:

```
vendas_do_dia = 20 × peso_dia_semana × peso_mês × peso_data_comercial × peso_quinzena × ruído
```

#### Dia da semana

| Dia | Peso | Racional |
| --- | --- | --- |
| Segunda | 0,70 | o vale da semana no shopping |
| Terça | 0,76 | |
| Quarta | 0,84 | |
| Quinta | 0,94 | |
| Sexta | 1,20 | começa o fim de semana |
| **Sábado** | **1,72** | o pico, com fluxo concentrado à tarde |
| Domingo | 0,92 | movimento alto, mas em 7h em vez de 12h |
| Feriado | 1,05 | horário de domingo, fluxo de passeio |

O sábado como pico e o início do pico às 11h estão documentados em
[dados de fluxo de varejo](https://vemcogroup.com/pt/blog/os-dias-de-maior-movimento-no-varejo-o-que-os-dados-de-fluxo-mostram-e-como-as-lojas-se-preparam).

#### Hora do dia

O fluxo não é uniforme dentro do expediente. São três curvas distintas:

| Janela | Formato | Picos |
| --- | --- | --- |
| Segunda a sexta | bimodal | almoço (12h–13h) e noite (18h–20h) |
| Sábado | concentrado à tarde | 14h–16h, começando a subir às 11h |
| Domingo e feriado | arco único | 16h–17h |

A curva de domingo é mais "cheia" por hora justamente porque o mesmo volume de clientes se espreme em 7 horas.

#### Mês e sazonalidade

| Mês | Peso | Mês | Peso |
| --- | --- | --- | --- |
| Janeiro | 1,05 | Julho | 1,00 |
| Fevereiro | 0,88 | **Agosto** | **0,97** |
| Março | 0,95 | **Setembro** | **0,93** |
| Abril | 0,97 | **Outubro** | **1,10** |
| Maio | 1,08 | Novembro | 1,25 |
| Junho | 1,02 | Dezembro | 1,45 |

Setembro é o vale do trimestre; outubro já puxa com o Dia das Crianças e a entrada da primavera.

#### Datas comerciais

Cada data tem uma **rampa**: o movimento cresce nos dias que antecedem e cai depois. Quem compra presente compra
na véspera — tipicamente no sábado anterior.

| Data | Pico | Janela |
| --- | --- | --- |
| Dia das Mães (2º domingo de maio) | 1,70× | 7 dias |
| Dia dos Namorados (12/06) | 1,45× | 6 dias |
| **Dia dos Pais (2º domingo de agosto)** | **1,55×** | 7 dias |
| **Dia do Cliente (15/09)** | **1,30×** | 4 dias |
| **Dia das Crianças (12/10)** | **1,40×** | 5 dias |
| Black Friday (última sexta de novembro) | 2,10× | 3 dias |
| Natal (24/12) | 2,30× | 12 dias |
| Liquidação de inverno (24–31/08) | 1,18× | — |

#### Quinzena

Dias 5 a 9 e 20 a 24 recebem **+10%**: são as janelas de pagamento de salário e adiantamento, quando o shopping
enche. É um efeito conhecido do varejo brasileiro e aparece claramente no gráfico de faturamento por dia.

---

### 14.5 Mix de produtos

A escolha do produto em cada venda é ponderada por **giro** — básico barato sai muito mais que peça de ticket
alto — e corrigida por **sazonalidade de categoria**.

| Produto | Giro | Produto | Giro |
| --- | --- | --- | --- |
| Camiseta Básica Branca M | 14% | Calça Jeans Skinny 40 | 8% |
| Kit Meias 3 Pares | 12% | Boné Aba Curva | 8% |
| Camiseta Básica Branca G | 11% | Cinto de Couro | 7% |
| Camiseta Estampada Preta P | 10% | Vestido Curto de Linho P | 7% |
| Calça Sarja Preta 42 | 9% | Vestido Midi Floral M | 6% |
| | | Jaqueta Jeans G | 5% |
| | | Jaqueta Couro Sintético M | 3% |

**Sazonalidade por família** (multiplicador sobre o giro):

- **Jaquetas**: 1,8 no inverno (jun/jul), 1,6 em agosto, 0,6 em outubro, 0,4 no verão.
- **Vestidos**: o inverso — 0,6 no inverno, 1,4 em outubro, 1,6 em dezembro.
- **Camisetas**: 0,8 no inverno, 1,3 no verão.
- **Calças e acessórios**: estáveis o ano todo.

**Deslocamento por data comemorativa**: no Dia dos Pais a procura muda de prateleira, não só de volume — calça
(1,50×), jaqueta (1,40×) e acessório (1,40×) sobem, vestido cai para 0,60×. No Dia das Crianças, camiseta sobe
para 1,50×.

---

### 14.6 Ticket médio e peças por venda

| Indicador | Modelo | Referência de mercado |
| --- | --- | --- |
| Peças por venda | 1,93 medido | 1,97 no 1º semestre de 2026 |
| Ticket médio | R$ 191,93 medido | R$ 271 no setor de moda |
| Produtos distintos por venda | 1 a 3 (62% / 29% / 9%) | — |

A quantidade por linha depende da faixa de preço: peça até R$ 69,90 sai em dupla ou trio com frequência; acima
de R$ 159,90 sai quase sempre uma só. O ticket resultante fica abaixo da média nacional do setor porque o
catálogo de demonstração é pequeno e concentrado em básicos — o que é coerente com uma loja de shopping
regional, não de capital.

Referências: [itens por pedido](https://bittencourtconsultoria.com.br/artigos/ticket-medio-no-e-commerce/) ·
[ticket médio do setor de moda](https://centraldovarejo.com.br/varejo-de-moda-projeta-r-63-bilhoes-em-vendas-na-temporada-de-outono-inverno-2026/).

---

### 14.7 Estoque: reposição e ruptura

Três meses de vendas consomem muito mais do que o estoque de abertura. O seeder simula a cadeia de suprimento:

1. **Carga inicial** no primeiro dia da janela, dimensionada por giro esperado.
2. **Reposição semanal**, toda **terça às 8h30** — antes de a loja abrir. O alvo é `máx(mínimo × 3, giro da
   semana × 2,2)`, ou seja, repõe o que girou com folga de segurança.
3. **Saída de estação**: nas duas últimas semanas a loja **para de repor jaquetas**, porque o verão está
   chegando. É o que produz, naturalmente, os alertas de estoque baixo e ruptura no dashboard.
4. **Ajustes de inventário** esporádicos (~22% das conferências semanais) com motivo registrado: peça com
   defeito retirada da arara, divergência na conferência.

Isso faz com que a tela de Estoque tenha um histórico real de entradas, saídas, ajustes e estornos — e não
apenas o "Estoque inicial".

---

### 14.8 Cancelamentos

Cerca de **2,5% das vendas** são canceladas de 0 a 3 dias depois, sempre com motivo (RN18) e devolvendo as peças
ao estoque via movimentação `SALE_CANCELLATION` (RN19). Os motivos vêm de uma lista realista: desistência do
cliente, peça com defeito, troca de tamanho, pagamento não autorizado.

No dashboard elas aparecem na contagem de canceladas e **saem do faturamento** — que só soma `CONFIRMED`.

---

### 14.9 Base de clientes

A demonstração passa de 6 para **51 clientes**, com nomes e endereços da região de Tubarão: Capivari de Baixo,
Laguna, Braço do Norte, Gravatal, Jaguaruna, Imbituba e Içara. Cada cadastro é anterior à primeira compra.

Os CPFs são **gerados com dígito verificador válido** — não são de pessoas reais, mas passam pela validação de
RN01, porque o cadastro é feito pelo `CustomerService` e não por SQL direto. **58% das vendas** têm cliente
identificado; o resto é consumidor de balcão, como numa loja de rua.

**Quem vendeu:** cada venda é creditada a um dos vendedores da equipe (hoje Vitor Nunes e Vinicius Muniz; Vinicius
Clemente passou a Operações e Compras em 29/10/2026) com pesos 5 : 4 : 3, num sorteio **separado** do sorteio principal, para a sequência de vendas
continuar idêntica à da seção 14.11. É o que alimenta o filtro **Vendedor(a)** do dashboard. Num banco criado
antes de 29/10/2026 (tudo no nome de um vendedor só), o seeder redistribui as vendas uma vez, na subida.

**Metas:** o `MetasSeeder` (depois do histórico) cria a meta de cada vendedor nos últimos 3 meses e no mês atual, a
partir do próprio histórico e arredondada para R$ 500: mês passado = vendido × fator; mês atual = vendido do mês
anterior × fator. Fatores 0,92 (Vitor, bate a meta) e 1,12 (Vinicius Muniz,
fica abaixo), para a demonstração ter as duas situações. Meta já gravada nunca é alterada pelo seed.

**Pedidos de compra:** o `PedidosCompraDemoSeeder` (depois das metas, só com a tabela vazia) cria 3 pedidos do
Vinicius Clemente com as peças que acabam primeiro: "Malharia Vale do Sul" chega em 3 dias, "Confecções Litoral SC"
em 10, e "Jeans Tubarão" está 2 dias atrasado. Assim a Reposição já abre com pedidos a caminho e um atrasado.

---

### 14.10 Por que SQL direto e não os Services

Esta é a decisão técnica que merece registro, porque contraria o padrão do `DevDataSeeder`.

Duas restrições do próprio sistema impedem gerar histórico pela aplicação:

1. **`SaleService.confirm()` grava `confirmed_at = OffsetDateTime.now()`** — não existe parâmetro de data. Toda
   venda criada pela API nasce "hoje".
2. **O trigger `trg_stock_movement_immutable` recusa UPDATE em `stock_movement`** (ver
   [V5](../../crm-loja-backend/src/main/resources/db/migration/V5__crm_product_purge.sql)). Então também não dá
   para criar pela API e corrigir a data depois: a movimentação de estoque ficaria com a data do boot.

O trigger, porém, **permite INSERT**. Por isso o histórico é inserido já com as datas corretas, em ordem
cronológica, via `JdbcTemplate`.

Como o SQL não passa pelas validações dos Services, o seeder assume as invariantes à mão:

| Invariante | Onde é garantida normalmente | Como o seeder cumpre |
| --- | --- | --- |
| `subtotal = unit_price × quantity` | `SaleItem.setQuantity` | calculado em `BigDecimal` com escala 2 |
| `sale.total` = soma dos subtotais | `Sale.recalculateTotal` | somado antes do INSERT |
| `balance_after` acumulado | `StockService.move` | saldo rastreado em memória, dia a dia |
| `product.stock_qty` = último saldo | `StockService.move` | `UPDATE` final por produto |
| Um item por produto na venda | `ux_sale_item_product` | carrinho usa `Set` de produtos já escolhidos |
| Estoque nunca negativo | `ck_product_stock` | quantidade limitada ao saldo disponível |

**Consequência colateral documentada:** como o `DevDataSeeder` criava as movimentações de "Estoque inicial" com
a data do boot — e o trigger impede corrigi-las — os produtos passaram a ser criados com **estoque zero**, e a
carga de abertura virou responsabilidade do `HistoricoVendasSeeder`, já retrodatada. Sem isso, o "Estoque
inicial" apareceria no topo do histórico como se fosse o movimento mais recente.

Na mesma linha, o histórico de movimentações passou a ser ordenado por **`created_at DESC`** em vez de
`id DESC` (`StockService.history` e `ProductService.detail`). Ordenar por id só funciona enquanto todo dado
nasce pela tela; qualquer carga histórica ou importação quebra a premissa.

---

### 14.11 Resultado medido

Números da execução de referência (janela 01/08/2026 a 28/10/2026):

| Indicador | Valor |
| --- | --- |
| Vendas confirmadas | 1.967 |
| Vendas canceladas | 44 (2,2%) |
| Faturamento | R$ 377.531,00 |
| Ticket médio | R$ 191,93 |
| Peças vendidas | 3.875 |
| Movimentações de estoque | 3.165 |
| Clientes | 51 |
| **Dias com venda** | **89 de 89** |

**Distribuição por dia da semana** — o sábado confirma-se como pico, com 2,4× o volume de segunda:

| Dia | Vendas | Ticket médio | Horário observado |
| --- | --- | --- | --- |
| Segunda | 201 | R$ 190,91 | 10h–21h |
| Terça | 231 | R$ 190,97 | 10h–21h |
| Quarta | 226 | R$ 185,12 | 10h–21h |
| Quinta | 238 | R$ 192,16 | 10h–21h |
| Sexta | 316 | R$ 189,96 | 10h–21h |
| **Sábado** | **490** | R$ 192,44 | 10h–21h |
| Domingo | 265 | R$ 200,56 | **14h–20h** |

**Feriados** — os quatro da janela operaram em horário de domingo, mesmo caindo em segunda e terça:

| Data | Dia | Vendas | Horário |
| --- | --- | --- | --- |
| 11/08 | terça | 23 | 14h–20h |
| 07/09 | segunda | 19 | 14h–20h |
| 15/09 | terça | 20 | 14h–20h |
| 12/10 | segunda | 30 | 14h–20h |

**Sazonalidade mensal** — setembro é o vale do trimestre, como o modelo previa:

| Mês | Vendas | Faturamento | Ticket médio |
| --- | --- | --- | --- |
| Agosto | 716 | R$ 143.891,70 | R$ 200,97 |
| Setembro | 583 | R$ 112.229,00 | R$ 192,50 |
| Outubro | 668 | R$ 121.410,30 | R$ 181,75 |

O **dia mais forte do trimestre é 08/08** — o sábado anterior ao Dia dos Pais — com 54 vendas e R$ 11.409,50,
seguido pela sexta 07/08. É o comportamento esperado de compra de presente.

**Troca de estação** — as curvas de jaqueta e vestido se cruzam dentro da janela, que é o efeito que derruba o
ticket médio de agosto para outubro:

| Categoria | Agosto | Setembro | Outubro |
| --- | --- | --- | --- |
| Jaquetas | 135 peças | 66 | 44 |
| Vestidos | 118 peças | 115 | 150 |

#### Verificação de integridade

Como o histórico é inserido por SQL, sem passar pelas validações dos Services, as invariantes foram conferidas
por consulta direta ao banco. Todas em zero:

| Verificação | Resultado |
| --- | --- |
| `sale.total ≠ Σ sale_item.subtotal` | 0 |
| `sale_item.subtotal ≠ unit_price × quantity` | 0 |
| `product.stock_qty ≠` último `balance_after` | 0 |
| Cadeia de saldo quebrada (`balance_after ≠ anterior + quantity`) | 0 |
| Venda cancelada sem estorno completo | 0 |
| `stock_movement.id` fora da ordem cronológica | 0 |
| Saldo negativo em qualquer movimentação | 0 |

---

### 14.12 Reprodutibilidade

O gerador usa `new Random(20260801L)` — **semente fixa**. O mesmo banco sai idêntico em toda execução, na
máquina do aluno, na banca e no CI. Não há `Math.random()` em lugar nenhum do seeder.

O seeder é **idempotente**: se já existir venda anterior a hoje, não faz nada. E é tolerante a falha — se
quebrar, loga o erro e a aplicação sobe assim mesmo, igual aos outros três seeders.

---

### 14.12b Depois da primeira subida: o agente de vendas (spec crm/08)

O histórico é criado uma vez. Dali em diante, o **agente de vendas** fecha cada dia que passou com o mesmo modelo
desta página (horário, dia da semana, mês, feriados, datas comerciais, quinzena, peso dos vendedores, reposição de
terça com o giro real da semana). Se houver IA configurada (a mesma da Thalita, grátis no Groq ou no Gemini), ela
escolhe só o "clima" do dia: fator de movimento entre 0,60 e 1,40, uma categoria em destaque e uma frase com o
motivo ("sexta de pagamento, shopping cheio"). Sem IA, o dia sai no padrão.

O agente nunca simula o dia de hoje e pula o dia se o sistema foi usado depois dele, para não contradizer o estoque
real. Tudo fica no **Diário do agente**, no Dashboard do gerente. Um workflow grátis do GitHub
(`acordar-demo.yml`) acorda o Render às 03:10 para o agente rodar com a loja fechada.

### 14.13 Fontes

- [Farol Shopping — horário de funcionamento](https://farolshopping.com.br/)
- [Prefeitura de Tubarão — calendário de feriados 2026 (Decreto nº 8.948)](https://tubarao.sc.gov.br/divulgado-calendario-de-feriados-municipais-e-pontos-facultativos-para-o-ano-de-2026/)
- [Central do Varejo — ticket médio do varejo de moda](https://centraldovarejo.com.br/varejo-de-moda-projeta-r-63-bilhoes-em-vendas-na-temporada-de-outono-inverno-2026/)
- [Grupo Bittencourt — itens por pedido e ticket médio](https://bittencourtconsultoria.com.br/artigos/ticket-medio-no-e-commerce/)
- [Vemco Group — dias e horários de maior movimento no varejo](https://vemcogroup.com/pt/blog/os-dias-de-maior-movimento-no-varejo-o-que-os-dados-de-fluxo-mostram-e-como-as-lojas-se-preparam)
