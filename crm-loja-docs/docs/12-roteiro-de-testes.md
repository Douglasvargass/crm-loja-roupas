# Roteiro de testes manuais

Checklist para testar o sistema pela tela. Comece com o banco zerado (apague `crm-loja-backend/.data` e rode `./mvnw spring-boot:test-run`), para os números de referência baterem.

Marque `[x]` em cada item que funcionou. Quando algo falhar, abra uma *Issue* no GitHub com: o passo, o que esperava, o que aconteceu, o perfil usado e um print.

**Dados de partida (seed):** 6 clientes, 12 produtos, 5 categorias, 4 vendas confirmadas e 1 cancelada. Estoque baixo: `CAM-002-G-BR` (3/5) e `CAL-001-40-AZ` (2/4). Sem estoque: `JAQ-001-M-PT`.

## 1. Acesso

- [ ] Entrar como `gerente@loja.local` / `loja12345` → abre o **Dashboard**.
- [ ] Sair e entrar como `vendedor@loja.local` → abre **Vendas**; o menu **não** tem Dashboard, Estoque nem Usuários.
- [ ] Sair e entrar como `estoquista@loja.local` → abre **Estoque**; o menu **não** tem Vendas nem Clientes.
- [ ] Senha errada → "Login ou senha incorretos" (mesma frase para login inexistente).
- [ ] Vendedor digita `/` na barra de endereço → é levado de volta para Vendas.
- [ ] Topo → **Trocar senha**: senha nova com menos de 8 caracteres é recusada; com 8 ou mais, funciona e dá para entrar com ela depois.
- [ ] Gerente → **Usuários → Nova conta** (papel Vendedor): aparece a senha provisória. Ao entrar com ela, a tela de troca de senha é obrigatória.
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
- [ ] Situação **Estoque baixo** → `CAM-002-G-BR` e `CAL-001-40-AZ`. **Sem estoque** → `JAQ-001-M-PT`.
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
- [ ] Buscar um produto: só aparecem os ativos com estoque (`JAQ-001-M-PT` não aparece).
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

## 8. Geral

- [ ] Fechar o navegador, abrir de novo → continua logado (até 24 h sem uso).
- [ ] Parar o back-end (`Ctrl + C`) e subir de novo → todos os dados continuam lá.
- [ ] Toda ação mostra aviso verde (sucesso) ou vermelho (erro), que some em uns 4 segundos.
- [ ] Excluir e cancelar sempre pedem confirmação.
- [ ] No celular (ou janela estreita), o menu vira um botão ☰ e as tabelas rolam de lado sem quebrar a página.
- [ ] Com o sistema em tema escuro, as telas continuam legíveis.

## 9. Assistente de IA (precisa de `ANTHROPIC_API_KEY`)

- [ ] Sem a chave → aviso amarelo "assistente desligado"; o campo de pergunta fica bloqueado.
- [ ] Gerente: "Quanto vendemos este mês?" → o valor bate com o Dashboard (período "Este mês"); embaixo aparece "Consultei: indicadores".
- [ ] Gerente: "O que preciso repor?" → lista os mesmos produtos do quadro "Precisa repor".
- [ ] Vendedor: "Tem camiseta tamanho M?" → responde com produtos e estoque reais.
- [ ] Vendedor: "Quanto a loja faturou?" → diz que o perfil não tem acesso.
- [ ] "Como cancelo uma venda?" → explica o caminho na tela e que só o gerente cancela.
- [ ] "Cadastre um cliente chamado X" → explica que só consulta e onde cadastrar.
- [ ] **Nova conversa** limpa o histórico; dentro da mesma conversa, uma pergunta de continuação ("e no mês passado?") entende o contexto.
