# Roteiro de testes manuais

Checklist para testar o sistema pela tela. Comece com o banco zerado (apague `crm-loja-backend/.data` e rode `./mvnw spring-boot:test-run`), para os números de referência baterem.

Marque `[x]` em cada item que funcionou. Quando algo falhar, abra uma *Issue* no GitHub com: o passo, o que esperava, o que aconteceu, o perfil usado e um print.

**Dados de partida (seed):** 5 categorias, 12 produtos, 51 clientes e cerca de 2.000 vendas distribuídas pelos
últimos 3 meses — uma por dia, no horário real do shopping. Os números exatos variam com a data de execução,
porque a janela é relativa a hoje; o [estudo dos dados](14-dados-de-demonstracao.md) explica o modelo.

Os alertas de estoque aparecem nas **jaquetas**, porque o seeder simula a saída de estação: nas duas últimas
semanas a loja deixa de repor essa categoria.

## 1. Acesso

- [ ] Entrar como `douglas.vargas` / `loja12345` (login sem @) → abre o **Dashboard**.
- [ ] Entrar como cada pessoa da equipe (senha `loja12345`): gerente (`guilherme.dower`), Engenheiro de Software (`douglas.vargas`) e Marketing e Desenvolvimento (`julia.alcantara`) abrem o **Dashboard** com o menu completo, inclusive **Usuários**; o topo mostra o papel de cada um; vendedores (`vitor.nunes`, `vinicius.muniz`) abrem **Meu painel** e o menu **não** tem Dashboard, Estoque nem Usuários; Operações e Compras (`vinicius.clemente`) abre **Chamados**, vê Produtos e Estoque e **não** tem Vendas nem Clientes.
- [ ] Senha errada → "Login ou senha incorretos" (mesma frase para login inexistente).
- [ ] Vendedor digita `/` na barra de endereço → é levado de volta para Vendas.
- [ ] Topo → **Trocar senha**: senha nova com menos de 8 caracteres é recusada; com 8 ou mais, funciona e dá para entrar com ela depois.
- [ ] Gerente → **Usuários → Nova conta** (papel Vendedor, login `nome.sobrenome`): aparece a senha provisória. Ao entrar com ela, a tela de troca de senha é obrigatória. Login com maiúscula ou espaço é recusado.
- [ ] Gerente tenta mudar o próprio papel → o seletor está bloqueado.

## 2. Clientes (gerente ou vendedor)

- [ ] **Novo cliente** com o CEP `88010-000` → rua, bairro, cidade e UF preenchem sozinhos (precisa de internet).
- [ ] Salvar sem nome → aviso embaixo do campo, nada é salvo.
- [ ] CPF `111.111.111-11` → "CPF inválido".
- [ ] CPF `529.982.247-25` (já existe) → "Já existe cliente com este CPF".
- [ ] E-mail `joao@` → "E-mail inválido".
- [ ] Cadastro correto → aviso verde e o cliente aparece na lista.
- [ ] Buscar `joao` (sem acento) → encontra "João Pedro Lima". Buscar `529.982` → encontra pelo CPF.
- [ ] Abrir um cliente → ficha com o resumo de compras e o histórico; clicar numa venda abre o detalhe dela.
- [ ] **Editar** → muda o telefone e salva.
- [ ] Gerente: **Excluir** um cliente com compras → oferece **Inativar**. Inativo some do filtro "Ativos" e não aparece na busca da Nova venda.
- [ ] Gerente: excluir o cliente recém-criado (sem compras) → removido.

## 3. Categorias

- [ ] Gerente: **Nova categoria** "Shorts" → aparece com 0 produtos.
- [ ] Criar "shorts" (minúsculo) → "Já existe uma categoria com este nome".
- [ ] Excluir "Camisetas" (tem produtos) → avisa que não pode e sugere inativar.
- [ ] Vendedor e estoquista veem a lista, mas sem botões de editar.

## 4. Produtos

- [ ] Gerente ou estoquista: **Novo produto** na categoria Shorts, preço `89,90`, estoque inicial `10`, mínimo `3` → salvo e com situação **Disponível**.
- [ ] Preço `0` → "O preço deve ser maior que zero". Código repetido → "Código já utilizado".
- [ ] Filtros combinados: categoria **Calças** + tamanho **40** → só `CAL-001-40-AZ`.
- [ ] Situação **Estoque baixo** e **Sem estoque** → os filtros trazem as jaquetas (`JAQ-001-M-PT` e/ou
      `JAQ-002-G-AZ`), que é onde a saída de estação simulada deixa o estoque cair. Confira contra o quadro
      **Precisa repor** do Dashboard: as duas telas têm de listar os mesmos produtos.
- [ ] Abrir o produto novo → movimentação "Entrada · Estoque inicial · +10".
- [ ] **Editar** o preço → a ficha mostra o novo preço; o estoque não é editável ali.
- [ ] Gerente: **Excluir** o produto novo (só tem estoque inicial) → removido.
- [ ] Gerente: excluir `CAM-001-M-BR` (tem vendas) → oferece **Desativar**; desativado, aparece como **Inativo**.

## 5. Estoque (gerente ou estoquista)

- [ ] O quadro **Precisa repor** lista os produtos zerados e os de estoque baixo.
- [ ] **Registrar entrada** de 5 un. em `CAL-001-40-AZ` → aviso "Saldo: 7"; sai da lista de estoque baixo.
- [ ] **Registrar ajuste** de `-2` com motivo "Avaria" → saldo cai 2. Sem motivo → pede o motivo.
- [ ] Ajuste que deixaria o saldo negativo → recusado com o saldo atual na mensagem.
- [ ] Entrada com quantidade `0` ou negativa → recusada.
- [ ] **Histórico**: filtrar por produto e por tipo "Ajuste" → mostra só o ajuste feito, com o saldo após.
- [ ] No Dashboard, **Registrar entrada** de um item da lista de estoque baixo → abre o Estoque com o produto já escolhido.

## 6. Vendas

- [ ] Vendedor: **Nova venda → escolher cliente → Abrir venda**.
- [ ] Buscar um produto: só aparecem os ativos com estoque — os que o Dashboard lista como **sem estoque** não
      podem aparecer aqui.
- [ ] Adicionar 2 produtos diferentes → subtotal de cada um e o total somam certo.
- [ ] Adicionar de novo o mesmo produto → soma na mesma linha.
- [ ] Pedir mais do que o estoque → aviso imediato e o botão Adicionar fica bloqueado.
- [ ] Botões `−` / `+` mudam a quantidade; a lixeira remove o item.
- [ ] **Confirmar venda** → status **Confirmada**; abrir os produtos e ver o estoque baixado e a movimentação "Venda #N".
- [ ] Venda confirmada: não dá mais para mexer nos itens.
- [ ] Venda sem itens → o botão Confirmar fica desabilitado.
- [ ] Vendedor não vê o botão **Cancelar venda**.
- [ ] Gerente: **Cancelar venda** sem motivo → pede o motivo; com motivo → **Cancelada**, estoque devolvido ("Estorno de venda" no histórico), data e motivo no resumo.
- [ ] **Vendas**: filtrar por número, por cliente, por período e por status.
- [ ] Editar o preço de um produto já vendido e abrir a venda antiga → mostra o preço da época.

## 7. Dashboard (gerente)

- [ ] Os cartões mostram clientes, produtos, vendas confirmadas, faturamento, ticket médio e canceladas.
- [ ] Trocar o período (Hoje, 7 dias, 30 dias, Este mês, Personalizado) → os números mudam.
- [ ] Confirmar uma venda nova e voltar ao Dashboard → vendas +1 e faturamento + total da venda.
- [ ] Cancelar essa venda → vendas −1, faturamento volta e canceladas +1.
- [ ] Mais vendidos, vendas por categoria e o gráfico por dia batem com as vendas feitas.
- [ ] **Filtros cruzados:** clicar numa barra de **categoria** filtra todo o painel (cartões, mais vendidos, gráfico por dia) e aparece um chip "Categoria: …". Clicar de novo na mesma barra remove o filtro.
- [ ] Clicar num **produto** do "Mais vendidos" e num **dia** do gráfico soma os filtros (3 chips). Cada gráfico continua mostrando as outras barras do próprio tipo, apagadas, para você trocar a seleção.
- [ ] Clicar no **x** de um chip ou em "Limpar filtros" desfaz. Ao desfazer tudo, os números voltam exatamente aos de antes.
- [ ] Com um **dia** filtrado, o período mostra só aquele dia e a comparação passa a ser com o dia anterior.
- [ ] Os cartões são links: "Clientes" e "Produtos" abrem as listas; "Vendas confirmadas", "Faturamento" e "Vendas canceladas" abrem **Vendas** já com status e período.
- [ ] **Filtro por vendedor(a):** a lista acima dos cartões traz os três vendedores. Escolher um muda cartões e os três gráficos para as vendas dele e aparece o chip "Vendedor(a): …"; "Todos" (ou o x do chip) volta ao total da loja. A soma dos três bate com "Todos".

## 8. Geral

- [ ] Fechar o navegador, abrir de novo → continua logado (até 24 h sem uso).
- [ ] Parar o back-end (`Ctrl + C`) e subir de novo → todos os dados continuam lá.
- [ ] Toda ação mostra aviso verde (sucesso) ou vermelho (erro), que some em uns 4 segundos.
- [ ] Excluir e cancelar sempre pedem confirmação.
- [ ] **Menu (3 traços), computador:** clicar nos 3 traços esconde o menu lateral; clicar de novo mostra. A escolha fica lembrada ao recarregar.
- [ ] **Menu (3 traços), celular ou janela estreita:** o menu começa escondido; tocar nos 3 traços abre uma gaveta (os traços viram X); fecha tocando no X, fora do menu, na tecla Esc ou ao escolher uma tela.
- [ ] No celular as listas (clientes, produtos, vendas…) aparecem como cartões, sem rolar para o lado, e nenhuma tela ganha barra de rolagem horizontal.
- [ ] A logo da Orden aparece no topo do menu, na tela de login e (menu escondido) no cabeçalho. No tema escuro ela fica sobre uma placa creme.
- [ ] Com o sistema em tema escuro, as telas continuam legíveis.

## 9. Thalita, a assistente de IA (precisa de um provedor de IA configurado)

A Thalita é o botão redondo no canto inferior direito, em todas as telas. Não existe mais "Assistente IA" no menu.

- [ ] Sem provedor configurado → no painel da Thalita aparece o aviso amarelo "desligada neste servidor" e o campo de mensagem fica bloqueado.
- [ ] Ao entrar, aparece um balãozinho "Oi! Eu sou a Thalita" por alguns segundos (uma vez por sessão do navegador).
- [ ] Digitar **oi** (ou "bom dia", "olá, Thalita") → ela se apresenta pelo seu primeiro nome, diz que é a assistente da Orden e sugere perguntas do seu perfil. A resposta é instantânea.
- [ ] No computador, **arraste a janela pelo cabeçalho** (a parte marrom com o nome Thalita) para qualquer lugar da tela; ela não sai da tela. Ao recarregar, ela fica onde você deixou. **Duplo clique** no cabeçalho devolve ao canto. No celular a janela é sempre em tela cheia.
- [ ] "Quem é você?" / "qual seu nome?" → a mesma apresentação.
- [ ] "Oi, quanto vendemos hoje?" **não** é só um oi: ela consulta e responde com números.
- [ ] Gerente: "Quanto vendemos este mês?" → o valor bate com o Dashboard (período "Este mês"); embaixo aparece "Consultei: indicadores". Ela cita as datas do período anterior.
- [ ] Gerente: "O que preciso repor?" → lista os mesmos produtos do quadro "Estoque baixo / Sem estoque".
- [ ] Vendedor: "Tem camiseta tamanho M?" → responde com produtos e estoque reais.
- [ ] Vendedor: "Quanto a loja faturou?" → diz que o perfil não tem acesso.
- [ ] "Como cancelo uma venda?" → explica o caminho na tela e que só o gerente cancela.
- [ ] "Cadastre um cliente chamado X" → explica que só consulta e onde cadastrar.
- [ ] O ícone de setas no topo do painel começa uma **nova conversa**; dentro da mesma conversa, "e no mês passado?" entende o contexto.
- [ ] A conversa continua ao trocar de tela (a janela só fecha no X ou na tecla Esc).
- [ ] Celular: a conversa abre em tela cheia e o botão redondo some enquanto ela está aberta.
- [ ] Com o menu do celular aberto, o botão da Thalita fica **atrás** do menu.
- [ ] Se o limite grátis do provedor acabar, a Thalita avisa "limite gratuito atingido" (e o "oi" continua funcionando, pois não usa a IA).

## 10. Meu painel e Metas (spec crm/06)

- [ ] Entrar como vendedor (`vitor.nunes`) → abre **Meu painel**, com a faixa "Você vê apenas os seus próprios números".
- [ ] O cartão da meta mostra "R$ vendido de R$ meta", a barra, a situação (No ritmo, Atenção…) e as frases "Faltam R$ X em N dias úteis: R$ Y por dia útil" e "No ritmo atual… você fecha o mês em R$ Z".
- [ ] Confirmar uma venda como esse vendedor e voltar ao painel → vendido, "Hoje" e "Minhas últimas vendas" mudam sozinhos.
- [ ] Clicar num dia do gráfico → aparece o total daquele dia; clicar de novo some.
- [ ] Trocar o mês para o anterior → "Mês fechado: …" e nada de ritmo.
- [ ] Vendedor digita `/metas` ou `/` na barra de endereço → volta para Meu painel. A Thalita, perguntada "quanto o Vinicius vendeu?", diz que o perfil não tem acesso.
- [ ] Entrar como gerente (`guilherme.dower`) → menu **Metas**. O quadro lista os 3 vendedores em ordem alfabética, com meta, vendido, %, projeção, falta e situação, e os totais da equipe.
- [ ] Mudar a meta de um vendedor → o botão Salvar aparece só quando o valor muda; ao salvar, aviso verde e a % se atualiza. Valor 0 → erro ao lado do campo.
- [ ] Escolher um mês passado → os campos viram texto e aparece "Este mês já fechou".
- [ ] Gerente na Thalita: "quem está mais longe da meta?" → responde com o quadro.

## 11. Agente de vendas da demonstração (spec crm/08)

- [ ] Gerente → Dashboard → cartão **Diário do agente de vendas**: cada dia com IA / Padrão / Pulado, motivo e vendas criadas.
- [ ] No dia seguinte a uma subida, "Este mês" e o Meu painel dos vendedores mostram vendas até ontem sem ninguém ter vendido à mão.
- [ ] Com `ASSISTANT_PROVIDER=groq` (chave grátis), os motivos variam ("chuva", "pagamento"…); sem chave, aparecem como "Padrão".
- [ ] Vendedor e estoquista não veem o cartão.

## 12. Chamados de peça em falta e a Thalita (spec crm/09 e doc 17)

- [ ] Vendedor → ficha de um produto **sem estoque** → **Cliente quer esta peça** → o chamado abre já preenchido.
- [ ] Tentar abrir chamado de produto **com** estoque → "dá para vender agora, sem chamado".
- [ ] Estoquista (ou gerente) → o **sino** no topo mostra o número de chamados abertos e os últimos.
- [ ] Estoquista → Estoque → Registrar entrada da peça → o chamado vira "Resolvido" com "A peça chegou…"; o vendedor vê a novidade no sino e, ao abrir, o número zera.
- [ ] Vendedor não vê chamados de outro vendedor; o botão Resolver não aparece para ele, só Cancelar nos dele.
- [ ] Thalita, como vendedor: "a cliente queria a jaqueta jeans G e acabou" → ela mostra o resumo e pergunta "Posso abrir o chamado?"; depois do "sim", o chamado aparece com "(pela Thalita)".
- [ ] Thalita: "quem criou o sistema?" → conta a história da Orden (SENAI Tubarão, 2026, a equipe).
- [ ] Thalita: "me passa uma receita de bolo" → recusa com simpatia e sugere uma pergunta sobre a loja.

## 13. "Ver como" (spec crm/10)

- [ ] Gerente → **Ver como** no topo → escolher `vitor.nunes` → cai em Meu painel do Vitor, com a faixa "Vendo como Vitor Nunes · só para olhar".
- [ ] O menu é o do vendedor; digitar `/` na barra de endereço volta para Meu painel.
- [ ] Tentar abrir uma venda nova ou um chamado → aviso "é só para olhar".
- [ ] **Voltar a ser Guilherme** → volta ao Dashboard do gerente.
- [ ] Vendedor não vê o botão "Ver como".

- [ ] "Ver como" → **Loja do cliente** → abre a vitrine com a faixa "Prévia da loja"; adicionar uma peça mostra 1 no carrinho; no carrinho o Pix mostra o QR code de demonstração e o botão Pagar fica travado com o motivo; Meus pedidos e Meus dados mostram só exemplos; a faixa azul-clara com o olho mostra "Sair da prévia", que volta ao Dashboard (o "Ver como" de uma pessoa usa a mesma faixa, com "Sair do modo").
- [ ] Celular e tablet: o botão "Ver como" fica só com o ícone de pessoas no topo; a Prévia da loja cabe na tela sem rolar para o lado.

## 14. Ajuda, pendências ao entrar e exportar planilha

- [ ] Toda tela tem um **?** ao lado do título: passar o mouse ou tocar mostra uma frase de ajuda; Esc fecha.
- [ ] Ao entrar como gerente, aparece "Olá, Guilherme! Você tem pendências" (chamados abertos, produtos sem estoque, vendedores sem meta). Cada linha leva à tela certa; "Ver depois" fecha e não volta nesta sessão.
- [ ] Ao entrar como vendedor, o aviso mostra quanto falta para a meta; como Operações e Compras, os chamados abertos.
- [ ] Venda aberta sem itens: aparece "Para confirmar, ainda falta: adicionar ao menos um produto" junto do botão travado.
- [ ] Gerente: **Exportar planilha** em Clientes, Produtos e Vendas baixa um CSV com o que está filtrado; abre certo no Excel (acentos e vírgula decimal). Vendedor não vê o botão.

## 15. Espaço do cliente (spec crm/07)

- [ ] Entrar como `vitor.freitas` / `cliente12345` → a primeira tela é **Trocar senha**; depois de trocar, cai na **Vitrine**, sem o menu da equipe.
- [ ] Digitar `/clientes` ou `/` na barra de endereço → volta para a Vitrine.
- [ ] Vitrine: só peças com estoque; "Últimas unidades" quando está no mínimo; nenhuma quantidade exata aparece.
- [ ] Adicionar duas peças ao carrinho → o número no ícone do carrinho sobe; o total é calculado.
- [ ] Escolher **Pix** → aparece um QR code de mentira com a faixa "DEMONSTRAÇÃO" e o aviso de que nada é cobrado.
- [ ] Pagar com **Pix** → "Pedido nº X confirmado"; em Meus pedidos, o pedido aparece; na equipe, Vendas mostra o canal **Online** e o estoque baixou.
- [ ] Pagar com cartão terminado em **0002** → "Pagamento não autorizado"; nada muda. Com outro cartão, aprova e mostra "final 1111".
- [ ] Gerente muda o preço de uma peça que está no carrinho → ao pagar, aviso "o preço mudou", total novo; pagar de novo funciona.
- [ ] Meus dados: muda telefone e endereço (o CEP preenche); nome e CPF ficam travados com "Só a loja muda".
- [ ] Gerente cancela o pedido online → em Meus pedidos aparece "Estornado"; o estoque volta.
- [ ] Gerente → Dashboard → **Canal: Online** → só os pedidos do espaço do cliente.

## 16. Reposição e pedidos de compra (spec crm/11)

- [ ] Entrar como `vinicius.clemente` → a primeira tela é **Reposição**; o aviso de pendências lista o pedido atrasado e as peças que acabam em 7 dias.
- [ ] Cartões no topo: zerados, acabam em 7 dias, para pedir, pedidos atrasados (clicar leva aos atrasados), chegam em 7 dias, dinheiro parado.
- [ ] Trocar "Pedir para cobrir" de 30 para 60 dias → a coluna Pedir aumenta.
- [ ] Marcar 2 peças → **Pedir marcadas (2)** → o novo pedido abre com as duas e a quantidade sugerida; informar fornecedor e registrar.
- [ ] Voltar à Reposição → as duas peças mostram "A caminho" com a data, e Pedir vira 0 ("já resolve").
- [ ] Pedidos de compra → **Mudar data** sem motivo → recusa; com motivo → "data mudou 1×".
- [ ] Abrir um chamado (como vendedor) de uma peça zerada que está num pedido; como Clemente, **Receber** esse pedido → "Entrada feita. 1 chamado fechado"; o estoque subiu e o vendedor vê a novidade no sino.
- [ ] **Cancelar** um pedido com motivo → vai para a aba Cancelados e as peças deixam de contar como a caminho.
- [ ] Entrar como estoquista (crie um em Usuários) → vê as duas telas e Receber, mas não vê Novo pedido, Mudar data nem Cancelar.
- [ ] Entrar como vendedor → o menu não mostra Reposição nem Pedidos de compra; digitar `/reposicao` não abre.
- [ ] Thalita (com a chave grátis ligada): "o que vai faltar essa semana?" e "tem pedido atrasado?" respondem com os números da tela.

## 17. Celular, tablet e Thalita online

- [ ] No celular (ou janela estreita): os 3 traços no topo abrem o menu; tocar num item troca de tela e o menu fecha sozinho.
- [ ] No tablet em pé: o mesmo menu em gaveta; deitado (1024 px ou mais), o menu fica fixo do lado.
- [ ] A Thalita aparece no canto em todas as telas da equipe; no celular a conversa abre em tela cheia, com o fechar no topo.
- [ ] Bolinha da Thalita: **verde** quando a IA está ligada (o topo da conversa mostra "● Online"); **cinza** quando falta a chave.

## 18. Dashboard de estoque, etapas do pedido e Thalita do cliente

- [ ] Entrar como `vinicius.clemente` → a primeira tela é o **Dashboard**, só com o bloco Estoque (sem faturamento).
- [ ] Gerente → Dashboard: as vendas de sempre e, abaixo, o mesmo bloco Estoque.
- [ ] Dashboard → cartão **Sugestão de pedido**: lista as peças a pedir com a quantidade; **Montar pedido com estas peças** abre o pedido de compra já preenchido (Clemente e gerente; o estoquista só vê a lista).
- [ ] Pedidos de compra → num pedido feito, **Saiu do fornecedor** (anotar a NF) → aba Em trânsito mostra a NF.
- [ ] **Chegou na loja** → aba Em conferência; "Mudar data" some.
- [ ] **Dar entrada** → a NF vem preenchida; sem NF o sistema recusa; confirmar → aba Entradas e o cartão "Notas que deram entrada" do Dashboard sobe.
- [ ] Cliente (`vitor.freitas`): a Thalita aparece no canto da loja; "Tem vestido M?" responde com a vitrine (com a chave da IA ligada).
- [ ] Vitrine → **Não achou? Peça à loja** → enviar → em Meus pedidos aparece em "Peças que pedi à loja" como "Aguardando a loja"; o gerente vê o chamado no sino; quando a peça entra no estoque, vira "Atendido".

## 19. Conferência do pedido, reset de senha e cliente com login (revisão de 29/10)

- [ ] Pedidos de compra → Em conferência → **Dar entrada**: mudar "Chegou" de uma peça para menos → aparece "faltam N", o botão Dar entrada trava com o motivo; **Devolver a nota** → o pedido volta para "Pedidos feitos" com "nota devolvida 1×" e o estoque não muda.
- [ ] Marcar "Chegou na loja" de novo → Dar entrada com uma peça a mais → pede a nota de bonificação; informar → "N un. a mais entraram pela nota de bonificação"; em Estoque, duas movimentações (NF e Bonificação).
- [ ] Reposição: cadastrar um produto novo com estoque → aparece como "Novo, sem histórico", não como "Parado"; o cartão Dinheiro parado não sobe.
- [ ] Usuários → **Resetar senha** de alguém → aparece a senha provisória; a pessoa entra com ela e é obrigada a trocar.
- [ ] Clientes → ficha do `vitor.freitas` (Vitor Freitas) → "Acesso à loja online: Sim" → **Resetar senha da loja** → senha provisória; **Excluir** → "tem acesso à loja online" e oferece inativar.

## 20. Previsão de vendas e metas pela previsão (spec 12)

- [ ] Gerente (`guilherme.dower`) → menu **Previsão de vendas** → cartões Próximos 7 dias, Próximos 30 dias, Fechamento do mês (com "já vendido até ontem") e os próximos 3 meses, cada um com "entre … e …".
- [ ] Trocar para 6 e 12 meses → aparecem mais cartões de mês.
- [ ] Gráfico: barras claras (vendido) até a linha tracejada de hoje e escuras (previsto) depois; pontos sobre feriados e datas comerciais; passar o mouse mostra o dia e a faixa.
- [ ] **Por que esse número**: nível atual, barras de Seg a Dom (sábado bem acima), semana do salário, feriado, mês do ano ("precisa de 1 ano de vendas") e o acerto das últimas 4 semanas.
- [ ] **Datas comerciais**: Dia das Crianças e Dia dos Pais "aprendida"; Black Friday e Natal "estimativa padrão"; a semana da Black Friday aparece mais alta no gráfico.
- [ ] Vendedor (`vitor.nunes`) e Operações e Compras (`vinicius.clemente`): o menu não tem Previsão de vendas; `/previsao` volta para a tela inicial.
- [ ] **Metas** → mês seguinte → bloco "Sugestão pela previsão de vendas" com a previsão da loja no mês; cada vendedor com a sugestão e "% da loja".
- [ ] Crescimento 10 → as sugestões sobem cerca de 10%; **Usar** num vendedor → a meta dele vira a sugestão e aparece "igual à meta".
- [ ] **Aplicar a todos** → **Confirmar N meta(s)** → todas as metas ficam iguais às sugestões, cada mudança registrada na auditoria.
- [ ] Mês que já fechou → o bloco de sugestão não aparece.
- [ ] Thalita (gerente): "quanto vamos vender em novembro?" responde com a faixa e o que pesou; "qual meta você sugere para o Vitor com 10% de crescimento?" responde com a sugestão e orienta a aplicar na tela Metas.

## 21. Fotos, cupom, criar conta e esqueci minha senha (30/10/2026)

- [ ] Menu **Catálogo** → 12 peças com foto; clicar numa peça → galeria com 4 fotos (frente, costas, lateral, detalhe); **Ampliar** → tela cheia; setas ← → e Esc funcionam; no celular, deslizar troca a foto.
- [ ] Ficha do produto (gerente) → **Enviar foto** (JPG/PNG até 2 MB) → aparece na galeria; **Usar como capa** → vira a foto do catálogo; **Excluir esta foto**. Arquivo que não é imagem → "Use uma imagem JPG, PNG ou WEBP".
- [ ] Loja online: cada peça da vitrine com foto; tocar abre as fotos em tela cheia.
- [ ] Menu **Cupons** (gerente) → BEMVINDO10 (10%) e ORDEN50 (R$ 50 acima de R$ 300) → **Novo cupom** → aparece na lista; **Desligar** / **Ligar**.
- [ ] Venda aberta (balcão) com peças → digitar `bemvindo10` → **Aplicar** → aparecem Peças, Desconto do cupom e o Total menor; **Confirmar** → em Cupons, Usos sobe 1.
- [ ] Carrinho do cliente abaixo de R$ 300 → `ORDEN50` → "Este cupom vale para pedidos a partir de R$ 300,00."; **Tirar** volta ao total cheio.
- [ ] Tela de entrada: "Bem-vindo(a) à Orden"; abas **Sou da equipe** e **Sou cliente**; em Sou cliente, **Criar conta** → preencher (o CEP completa o endereço) → já entra na vitrine.
- [ ] Criar conta com o mesmo e-mail → "Já existe uma conta com este e-mail"; sem marcar o aceite → pede o aceite.
- [ ] **Esqueci minha senha** → e-mail → mensagem "Se este e-mail tiver conta, enviamos um link…" (igual para e-mail sem conta). No localhost, sem SMTP, o link aparece no terminal do back-end; abrir → **Criar uma senha nova** → entrar com a nova. Abrir o mesmo link de novo → "não vale mais".
