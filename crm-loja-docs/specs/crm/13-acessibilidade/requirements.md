# Requisitos — Acessibilidade: Aparência, Libras e navegação (crm/13)

> Épico: Transversal · Histórias: HU64, HU65, HU66 · Solicitante: Douglas Vargas (equipe) · Origem: conversa de 30/10/2026 ("pensando em acessibilidade: botão dia/noite, pessoas com sensibilidade, surdas, Libras") · Data: 30/10/2026
> Depende de: todas as telas (só front-end) · Banco: nenhuma migration
> Prioridade (MoSCoW): Should have · Tamanho: M (cerca de 20 arquivos, sem back-end)
> Status: implementada em 30/10/2026 (conferida no localhost; ainda sem a opinião de uma pessoa usuária de leitor de tela ou intérprete)

## Contexto

O sistema é usado por equipe e por clientes, e nem todos enxergam, leem ou ouvem do mesmo jeito. Havia só um visual (claro,
letra fixa) e avisos que sumiam em 4 segundos. Para muita gente surda a primeira língua é a Libras, e o português escrito é a
segunda: texto na tela não basta.

**O visual claro oficial NÃO muda** (preto e branco com o Dashboard na paleta Ice, definido pelo Douglas em 30/10/2026): esta
spec só acrescenta opções que a pessoa liga se quiser.

## Objetivo

Como **pessoa com sensibilidade à luz, baixa visão ou surda**, quero ajustar a aparência e ter tradução para Libras, para usar o
sistema sem esforço.

## Requisitos

### REQ-01 — Botão "Aparência" em toda tela (HU64)

Um botão no topo (equipe, loja do cliente e login) abre uma janela com:

- **Tema:** Claro (padrão, o visual oficial), Escuro (cinza bem escuro, nunca preto puro, com o Dashboard Ice em tons mais claros) e Automático (segue o computador ou o celular, e troca sozinho quando ele troca).
- **Tamanho da letra:** Normal, Grande (112,5%) e Maior (125%): tudo o que usa rem cresce junto (textos, botões, espaços).
- **Alto contraste:** texto preto (ou branco no escuro), bordas fortes, cores de situação mais escuras e links do conteúdo sublinhados.
- **Reduzir movimento:** desliga animações e transições. Já começa ligado se o aparelho pediu menos movimento.
- **Restaurar o padrão.**
- A escolha vale na hora, fica salva **no navegador de cada pessoa** (localStorage) e é aplicada antes de a tela aparecer (sem piscar). Se o navegador não guardar nada (janela privativa), tudo funciona igual, só sem lembrar.

### REQ-02 — Contraste conferido (RN70)

- Nos modos que esta spec cria (escuro, alto contraste claro e alto contraste escuro): texto com 4,5:1 ou mais (7:1 no alto contraste) e borda de campo digitável com 3:1 ou mais (WCAG 1.4.11), com a cor própria `--border-input`.
- No tema escuro, menu lateral e barra de cima escurecem junto (a moldura branca vale só no claro).
- **Visual padrão (oficial) fica como está**, mesmo onde o verde, o amarelo e o vermelho do Dashboard Ice ficam um pouco abaixo de 4,5:1 (6 pares, 3,1 a 4,2): quem precisa de mais contraste liga "Alto contraste", que passa em tudo.
- Conferência nos quatro modos: `docs/qa/verificar-contraste.py` (pares de cores) e uma varredura de todos os textos de 16 telas no navegador (1.618 textos por modo): 0 falhas no escuro e nos dois alto contraste.

### REQ-03 — Tradutor de Libras, o VLibras (HU65, RN72)

- O plugin oficial do governo federal (gratuito) aparece como um botão azul na borda da tela; ao selecionar um texto, o boneco o traduz para Libras.
- **Ligado por padrão** (quem precisa não deve ter de procurar) e pode ser desligado em Aparência. Só carrega o script externo se estiver ligado.
- O script é a versão **fixa 7.12.2** (não muda sozinha). Falha de internet ou do servidor do governo é silenciosa: o sistema segue normal.
- Aviso na própria janela: o texto selecionado é enviado ao servidor do VLibras para ser traduzido; a tradução é automática e pode errar (termos do sistema, como "nota de bonificação"). Não substitui intérprete humano.

### REQ-04 — Avisos e teclado (HU66, RN71)

- Avisos de sucesso ficam 8 s e os de erro 12 s (antes 4 s), **pausam** com o mouse ou o foco em cima e têm botão **×** para fechar.
- "Pular para o conteúdo": primeiro item do Tab em toda tela; leva o foco para o conteúdo principal.
- Foco visível em tudo (contorno de 3 px).
- Todo botão, link e campo tem nome para leitor de tela (conferido em 17 telas, 0 sem nome).

## Não inclui

Vídeos em Libras gravados por intérprete (o conteúdo de ajuda continua em texto simples), legenda em vídeo (o sistema não tem vídeo nem som), teste com leitor de tela real por pessoa usuária, tema por conta de usuário (a escolha é do navegador, não da conta), mudança nas cores oficiais.

## Riscos

- A tradução automática do VLibras erra termos técnicos: recomenda-se manter o português simples (já é regra do projeto).
- Texto em px fixo (poucos rótulos de 11 px) não cresce com "Maior".
- A escolha por navegador significa que quem troca de computador precisa escolher de novo.
- O filtro que deixa logo e ícones em cinza (visual oficial) continua valendo em todos os temas.
