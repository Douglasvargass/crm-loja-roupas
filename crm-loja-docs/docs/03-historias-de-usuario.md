## 4. Histórias de usuário

São 57 histórias em 13 épicos (HU01 a HU35 são o MVP do enunciado; HU36 a HU54 vieram depois, sem mexer no que é obrigatório), todas rastreáveis aos critérios do enunciado. A prioridade usa MoSCoW: **Must** é obrigatório para o MVP, **Should** é desejável e **Could** é extra depois do essencial.

**Personas:** **Vendedor(a)** atende o balcão e registra vendas. **Estoquista** recebe mercadoria e faz ajustes. **Gerente** acompanha resultados e cadastros. Cada persona é um papel do módulo crm; o Gerente é o administrador do módulo.

### Backlog resumido

| ID | História | Persona | Prioridade |
| --- | --- | --- | --- |
| HU01 | Cadastrar cliente | Vendedor | Must |
| HU02 | Listar e pesquisar clientes | Vendedor | Must |
| HU03 | Ver detalhes do cliente | Vendedor | Must |
| HU04 | Editar cliente | Vendedor | Must |
| HU05 | Excluir ou inativar cliente | Gerente | Must |
| HU06 | Ver histórico de compras do cliente | Vendedor | Must |
| HU07 | Cadastrar categoria | Gerente | Must |
| HU08 | Listar, editar e excluir categoria | Gerente | Must |
| HU09 | Cadastrar produto | Estoquista | Must |
| HU10 | Listar e pesquisar produtos | Vendedor | Must |
| HU11 | Ver detalhes do produto | Vendedor | Must |
| HU12 | Editar produto | Estoquista | Must |
| HU13 | Excluir ou inativar produto | Gerente | Must |
| HU14 | Identificar ativos e indisponíveis | Vendedor | Must |
| HU15 | Filtrar produtos por categoria | Vendedor | Must |
| HU16 | Ver saldo de estoque | Estoquista | Must |
| HU17 | Registrar entrada de estoque | Estoquista | Must |
| HU18 | Registrar ajuste de estoque | Estoquista | Must |
| HU19 | Consultar estoque baixo e zerado | Gerente | Must |
| HU20 | Ver histórico de movimentações | Gerente | Must |
| HU21 | Abrir venda e vincular cliente | Vendedor | Must |
| HU22 | Adicionar e remover itens | Vendedor | Must |
| HU23 | Confirmar venda | Vendedor | Must |
| HU24 | Cancelar venda | Gerente | Must |
| HU25 | Consultar vendas | Gerente | Must |
| HU26 | Ver detalhes da venda | Vendedor | Must |
| HU27 | Indicadores gerais no dashboard | Gerente | Must |
| HU28 | Vendas e faturamento por período | Gerente | Must |
| HU29 | Mais vendidos | Gerente | Must |
| HU30 | Alertas de estoque no dashboard | Gerente | Must |
| HU31 | Navegação e feedback | Todos | Must |
| HU32 | Exportar listas para CSV | Gerente | Could |
| HU33 | Entrar no sistema | Todos | Must |
| HU34 | Trocar a senha | Todos | Must |
| HU35 | Gerenciar usuários e papéis | Gerente | Should |
| HU36 | Tirar dúvidas com a Talita (assistente de IA) | Todos | Could |
| HU37 | Meu painel: minhas vendas e minha meta | Vendedor | Should |
| HU38 | Definir a meta de cada vendedor | Gerente | Should |
| HU39 | Quadro de metas da equipe | Gerente | Should |
| HU40 | Entrar como cliente e trocar a senha | Cliente | Could |
| HU41 | Vitrine de produtos | Cliente | Could |
| HU42 | Carrinho | Cliente | Could |
| HU43 | Pagar (Pix ou cartão simulados) | Cliente | Could |
| HU44 | Meus pedidos | Cliente | Could |
| HU45 | Meus dados | Cliente | Could |
| HU46 | Agente de vendas da demonstração | Equipe | Could |
| HU47 | Abrir chamado de peça em falta | Vendedor | Should |
| HU48 | Resolver ou cancelar chamado | Estoquista | Should |
| HU49 | Sino de avisos | Todos | Should |
| HU50 | "Ver como" (só leitura) | Gerente | Should |
| HU51 | Ver o que vai faltar e quanto pedir | Operações e Compras | Should |
| HU52 | Registrar pedido de compra | Operações e Compras | Should |
| HU53 | Receber o pedido de compra | Estoquista | Should |
| HU54 | Mudar a data ou cancelar o pedido de compra | Operações e Compras | Should |
| HU55 | Acompanhar as etapas do pedido e a nota fiscal | Operações e Compras | Should |
| HU56 | Ver o estoque no Dashboard | Operações e Compras | Should |
| HU57 | Falar com a Talita e pedir uma peça (cliente) | Cliente | Could |

### EP01 · Clientes

**HU01 · Cadastrar cliente.** Como vendedor, quero cadastrar um cliente com nome, CPF, telefone, e-mail e endereço, para identificá-lo nas vendas.

- Dado o formulário preenchido corretamente, quando salvo, então o cliente aparece na lista e vejo "Cliente cadastrado com sucesso".
- Dado um campo obrigatório vazio, quando salvo, então o campo fica destacado e nada é gravado.
- Dado um CPF com dígito verificador inválido, quando salvo, então vejo "CPF inválido".
- Dado um CPF já cadastrado, quando salvo, então vejo "Já existe cliente com este CPF".
- Dado um e-mail fora do formato, quando salvo, então vejo "E-mail inválido".

**HU02 · Listar e pesquisar clientes.** Como vendedor, quero ver a lista e buscar por nome ou CPF, para achar o cliente no atendimento.

- A lista mostra nome, CPF formatado, telefone e situação, paginada de 10 em 10.
- A busca por nome é parcial e ignora maiúsculas e acentos ("joao" encontra "João").
- A busca por CPF aceita com ou sem pontuação.
- Sem resultados, aparece "Nenhum cliente encontrado".

**HU03 · Ver detalhes do cliente.** Como vendedor, quero abrir a ficha completa do cliente, para conferir dados e compras.

- A ficha mostra todos os campos, a data de cadastro e o resumo de compras (quantidade e valor total confirmado).

**HU04 · Editar cliente.** Como vendedor, quero alterar os dados do cliente, para mantê-los atualizados.

- Valem as mesmas validações do cadastro.
- Alterar para um CPF de outro cliente é recusado.
- As vendas antigas continuam vinculadas ao cliente.

**HU05 · Excluir ou inativar cliente.** Como gerente, quero remover clientes sem perder histórico, para manter a base limpa.

- Dado um cliente sem vendas, quando excluo e confirmo, então ele é apagado.
- Dado um cliente com vendas, quando tento excluir, então vejo "Cliente possui 3 venda(s) e não pode ser excluído. Deseja inativá-lo?".
- Cliente inativo não aparece para novas vendas, mas continua no histórico.

**HU06 · Ver histórico de compras.** Como vendedor, quero ver as compras do cliente, para atender melhor e sugerir produtos.

- Lista número, data, quantidade de itens, total e status de cada venda, da mais recente para a mais antiga.
- Clicar numa venda abre o detalhe (HU26).

### EP02 · Categorias

**HU07 · Cadastrar categoria.** Como gerente, quero cadastrar categorias (camisetas, calças, vestidos, jaquetas, acessórios), para organizar os produtos.

- Nome obrigatório e único, sem diferenciar maiúsculas ("Calças" = "calças").

**HU08 · Listar, editar e excluir categoria.** Como gerente, quero manter as categorias, para corrigir e reorganizar.

- A lista mostra quantos produtos cada categoria tem.
- Categoria com produtos não pode ser excluída: "Categoria possui 12 produto(s) vinculados".
- Categoria inativa não aparece no cadastro de novos produtos.

### EP03 · Produtos

**HU09 · Cadastrar produto.** Como estoquista, quero cadastrar um produto com nome, descrição, categoria, tamanho, cor, código, preço e estoque inicial, para colocá-lo à venda.

- Código único; duplicado mostra "Código já utilizado".
- Preço deve ser maior que zero; estoque inicial e estoque mínimo, maiores ou iguais a zero.
- Estoque inicial maior que zero gera uma movimentação `ENTRY` com a observação "Estoque inicial".

**HU10 · Listar e pesquisar produtos.** Como vendedor, quero buscar por nome, categoria, tamanho ou código, para encontrar a peça rápido.

- Os filtros combinam entre si (ex.: categoria "Calças" + tamanho "40").
- A lista mostra código, nome, categoria, tamanho, cor, preço, estoque e situação.

**HU11 · Ver detalhes do produto.** Como vendedor, quero ver a ficha do produto, para informar o cliente.

- Mostra todos os campos, as últimas movimentações e o total vendido.

**HU12 · Editar produto.** Como estoquista, quero alterar dados do produto, para corrigir informações e preço.

- O estoque **não** é editável aqui; muda só por entrada, ajuste ou venda.
- Mudar o preço não altera vendas já registradas.

**HU13 · Excluir ou inativar produto.** Como gerente, quero remover produtos sem quebrar o histórico.

- Produto sem vendas e sem movimentações (além da inicial) pode ser excluído.
- Produto com vínculos só pode ser inativado.

**HU14 · Identificar ativos e indisponíveis.** Como vendedor, quero ver claramente o que posso vender.

- Etiquetas: **Disponível** (ativo e estoque > 0), **Sem estoque** (ativo e estoque = 0), **Inativo**.
- Filtro por situação: todos, disponíveis, sem estoque, estoque baixo, inativos.

**HU15 · Filtrar por categoria.** Como vendedor, quero filtrar a lista por categoria, para ver só um tipo de peça.

### EP04 · Estoque

**HU16 · Ver saldo.** Como estoquista, quero ver a quantidade disponível de cada produto, para saber o que repor.

**HU17 · Registrar entrada.** Como estoquista, quero lançar a chegada de mercadoria, para aumentar o saldo.

- Quantidade deve ser maior que zero.
- Gera movimentação `ENTRY` com data, quantidade, saldo após e observação (ex.: nota do fornecedor).

**HU18 · Registrar ajuste.** Como estoquista, quero corrigir o saldo (perda, avaria, inventário), para que o sistema bata com a prateleira.

- Quantidade com sinal (+ ou −), diferente de zero, com motivo obrigatório.
- Ajuste que deixaria o saldo negativo é recusado: "Saldo atual 3; ajuste de −5 deixaria estoque negativo".

**HU19 · Estoque baixo e zerado.** Como gerente, quero listar produtos abaixo do mínimo e sem estoque, para planejar compras.

- Baixo = `0 < estoque ≤ estoque_minimo`; zerado = `estoque = 0`.

**HU20 · Histórico de movimentações.** Como gerente, quero ver todas as movimentações com filtros por produto, tipo e período, para auditar o estoque.

- Cada linha: data/hora, produto, tipo, quantidade, saldo após, venda de origem (se houver), observação.

### EP05 · Vendas

**HU21 · Abrir venda.** Como vendedor, quero iniciar uma venda e, se o cliente se identificar, vinculá-lo, para registrar a compra.

- Cliente é opcional; a venda sem cliente aparece como "Consumidor não identificado".
- A venda nasce com status `DRAFT` (na tela, Aberta) e total zero.

**HU22 · Adicionar e remover itens.** Como vendedor, quero incluir produtos e quantidades, para montar o pedido.

- Só produtos ativos com estoque > 0 aparecem na busca.
- O preço unitário é copiado do produto no momento da inclusão.
- Subtotal = quantidade × preço; total = soma dos subtotais, recalculados na hora.
- Incluir o mesmo produto de novo soma a quantidade na mesma linha.
- Quantidade acima do disponível mostra aviso imediato.

**HU23 · Confirmar venda.** Como vendedor, quero confirmar a venda, para concluir e baixar o estoque.

- Venda sem itens não pode ser confirmada.
- O estoque de **todos** os itens é revalidado na confirmação; se um faltar, nada é baixado e a mensagem diz qual produto falhou.
- Ao confirmar: status `CONFIRMED`, data de confirmação gravada, uma movimentação `SALE` por item.
- Confirmar de novo uma venda já confirmada é recusado (sem baixa duplicada).
- Venda confirmada não pode ter itens alterados.

**HU24 · Cancelar venda.** Como gerente, quero cancelar uma venda informando o motivo, para corrigir erros ou desistências.

- Motivo obrigatório.
- Venda `CONFIRMED` cancelada gera `SALE_CANCELLATION` por item e devolve o estoque.
- Venda `DRAFT` cancelada não mexe no estoque.
- Venda já `CANCELLED` não pode ser cancelada de novo.
- A venda cancelada continua visível no histórico.

**HU25 · Consultar vendas.** Como gerente, quero buscar vendas por número, cliente, período e status.

- Filtros combináveis; lista mostra número, data, cliente, itens, total, status.

**HU26 · Ver detalhes da venda.** Como vendedor, quero abrir uma venda e ver itens, preços praticados e status.

- Os preços exibidos são os da venda, mesmo que o produto tenha mudado de preço depois.
- Venda cancelada mostra data e motivo do cancelamento.

### EP06 · Dashboard

**HU27 · Indicadores gerais.** Como gerente, quero ver total de clientes e de produtos cadastrados.

**HU28 · Vendas por período.** Como gerente, quero escolher um período e ver quantidade de vendas e valor faturado.

- Atalhos: Hoje, 7 dias, 30 dias, Este mês, Personalizado. Padrão: Este mês.
- Contam só vendas `CONFIRMED` (canceladas aparecem num contador à parte).
- Lista **Vendedor(a)** ("Todos" ou uma pessoa): cartões e gráficos passam a mostrar só as vendas abertas por ela, para saber quanto cada um vendeu.

**HU29 · Mais vendidos.** Como gerente, quero ver o top 5 de produtos por quantidade vendida no período, em gráfico de barras.

**HU30 · Alertas de estoque.** Como gerente, quero ver as listas de estoque baixo e zerado, com atalho para registrar entrada.

- Todos os indicadores vêm de consulta ao banco a cada carregamento; nenhum valor é fixo.

### EP07 · Transversal

**HU31 · Navegação e feedback.** Como usuário, quero um menu fixo e mensagens claras, para usar o sistema sem treinamento.

- Menu lateral com os módulos que o papel pode usar, item ativo destacado; toda tela tem o "?" de ajuda.
- Toda operação mostra aviso de sucesso (verde) ou erro (vermelho) que some sozinho em 4 segundos.
- Exclusões e cancelamentos pedem confirmação.
- Erros de validação aparecem ao lado do campo.

**HU32 · Exportar CSV (extra).** Como gerente, quero exportar listas de clientes, produtos e vendas para planilha.

### EP08 · Acesso

**HU33 · Entrar no sistema.** Como usuário, quero entrar com login e senha, para usar só o que meu papel permite.

- Login e senha corretos abrem a tela inicial do papel (Gerente e equipe técnica: Dashboard; Vendedor: Meu painel; Estoquista: Estoque; Operações e Compras: Reposição; Cliente: Vitrine).
- Login ou senha errados, conta inativa ou bloqueada mostram a mesma frase: "Login ou senha incorretos".
- A sessão dura enquanto a pessoa usa o sistema; parada por mais de 24 h, pede login de novo.
- Botão Sair encerra a sessão neste navegador.

**HU34 · Trocar a senha.** Como usuário, quero trocar minha senha, para manter a conta segura.

- No primeiro acesso a troca é obrigatória antes de qualquer outra tela.
- Senha nova com pelo menos 8 caracteres e diferente da atual.
- Trocar a senha encerra as sessões abertas em outros navegadores.

**HU35 · Gerenciar usuários e papéis.** Como gerente, quero criar contas e definir o papel de cada pessoa, para controlar quem faz o quê.

- Cria conta com senha provisória e troca obrigatória no primeiro acesso.
- Muda o papel ou inativa a conta; a mudança fica registrada na auditoria.
- Reseta a senha de quem esqueceu (equipe em Usuários; cliente na ficha dele): senha provisória, troca obrigatória, sessões encerradas.
- O gerente não muda o próprio papel, e a loja nunca fica sem nenhum gerente ativo (validado dentro da transação).

### EP09 · Assistente de IA

**HU36 · Tirar dúvidas com a Talita (extra).** Como membro da equipe, quero perguntar em linguagem natural sobre vendas, estoque, clientes e uso do sistema, para decidir sem procurar em várias telas.

- As respostas usam dados consultados no banco no momento; nada é inventado.
- Cada perfil só obtém o que a tela já mostraria a ele.
- Ela só fala da loja: receita, notícia ou lição de casa ela recusa com simpatia.
- O assistente só consulta; a única exceção é abrir chamado de peça em falta, e só depois de a pessoa confirmar (HU47).
- Sem a chave da IA configurada, o assistente aparece desligado e o resto do sistema funciona.

### EP10 · Metas e painel do vendedor (spec crm/06)

**HU37 · Meu painel.** Como vendedor, quero ver as minhas vendas do mês, a minha meta e quanto falta por dia, para saber se estou no caminho.

- Mostra vendido no mês, meta, quanto falta, ritmo por dia útil (segunda a sábado) e a projeção do mês.
- Cada vendedor vê só os próprios números, sem comparação com colegas.
- Sem meta definida, o painel mostra as vendas e avisa que a meta ainda não foi definida.

**HU38 · Definir a meta de cada vendedor.** Como gerente, quero definir a meta de cada vendedor por mês, para acompanhar a equipe com um alvo claro.

- Só o mês atual e os próximos 12 meses aceitam meta; mês fechado não muda.
- Só vendedor ativo recebe meta; valor maior que zero.
- Toda mudança vai para a auditoria (de quanto para quanto).

**HU39 · Quadro de metas da equipe.** Como gerente, quero ver a equipe toda num quadro, para saber quem precisa de ajuda.

- Uma linha por vendedor com vendido, meta, projeção e a situação (bateu, no ritmo, atenção, abaixo, sem meta).

### EP11 · Espaço do cliente (spec crm/07)

**HU40 · Entrar como cliente.** Como cliente, quero entrar com o meu login, para comprar pela internet.

- A conta do cliente é separada da equipe: ele só vê a loja (`/loja`) e nunca o sistema interno.
- Primeiro acesso com a senha padrão e troca obrigatória.

**HU41 · Vitrine.** Como cliente, quero ver as peças disponíveis, para escolher o que comprar.

- Só peças ativas e com estoque; "Últimas unidades" quando está no mínimo; a quantidade exata não aparece.

**HU42 · Carrinho.** Como cliente, quero montar um carrinho, para comprar várias peças de uma vez.

- Adicionar, mudar a quantidade e remover; o total é calculado sozinho.
- Se o preço mudar com a peça no carrinho, vale o preço novo e o cliente confere antes de pagar.

**HU43 · Pagar.** Como cliente, quero pagar com Pix ou cartão, para fechar o pedido.

- Pagamento simulado: nada é cobrado; o Pix mostra um QR code de demonstração; cartão final 0002 é recusado, para testar.
- Fechar o pedido é tudo ou nada: se faltar estoque de uma peça, nada é baixado.
- Só os 4 últimos dígitos do cartão são guardados.

**HU44 · Meus pedidos.** Como cliente, quero acompanhar os meus pedidos, para saber o que comprei.

- Lista e detalhe só dos meus pedidos; pedido de outro cliente não existe para mim (404).
- Pedido cancelado pela loja aparece como estornado.

**HU45 · Meus dados.** Como cliente, quero atualizar telefone, e-mail e endereço, para manter o cadastro certo.

- Nome e CPF só a loja muda.

### EP12 · Dados de demonstração (spec crm/08)

**HU46 · Agente de vendas da demonstração.** Como equipe, quero que a demonstração continue vendendo sozinha todo dia, para o dashboard nunca ficar parado.

- Fecha um dia por vez, sempre no passado, com o mesmo jeito de vender do histórico.
- A IA grátis só escolhe o "clima" do dia (mais ou menos movimento); os números vêm do sistema.
- Se houve uso real naquele dia, o agente não mexe; o gerente vê o diário no Dashboard.

### EP04 · Estoque (continuação)

**HU47 · Abrir chamado de peça em falta (spec crm/09).** Como vendedor, quero registrar a peça que o cliente queria e não tinha, para o estoque repor e eu avisar o cliente quando chegar.

- Descrição, tamanho, cor, quantidade (1 a 99), produto e cliente opcionais.
- Peça com estoque suficiente não abre chamado: é para vender.
- A Talita também abre, sempre mostrando o resumo e esperando o "sim".

**HU48 · Resolver ou cancelar chamado.** Como estoquista ou gerente, quero fechar o chamado com uma nota, para registrar o que foi feito.

- Resolver: estoque e gerente; cancelar: esses ou quem abriu; sempre com nota.
- A entrada de estoque da peça resolve sozinha os chamados mais antigos que ela cobre.

**HU49 · Sino de avisos.** Como membro da equipe, quero ver no topo o que precisa da minha atenção.

- Estoque e gerente veem os chamados abertos; o vendedor vê as novidades dos chamados dele.

**HU51 · Ver o que vai faltar (spec crm/11).** Como Operações e Compras, quero ver o que vai faltar antes de faltar e quanto pedir, para comprar na hora certa.

- Média de venda por dia nos últimos 30 dias, dias até acabar, o que está a caminho, chamados abertos e quanto pedir.
- Situações: zerado, acaba em até 7 dias, até 21 dias, tranquilo e parado (dinheiro empatado).
- Escolher quantos dias o pedido deve cobrir (7 a 90).

**HU52 · Registrar pedido de compra.** Como Operações e Compras, quero registrar o que pedi ao fornecedor e quando chega, para a loja saber o que está a caminho.

- Fornecedor, data prevista (hoje a 180 dias) e de 1 a 30 peças; o pedido pode vir montado da Reposição.
- O estoquista não registra pedido: ele recebe a mercadoria.

**HU53 · Receber o pedido.** Como estoquista, quero dar entrada no pedido que chegou com um clique, para o estoque subir e os chamados fecharem.

- Cada peça entra no estoque na mesma transação (tudo ou nada); pedido recebido não recebe de novo.

**HU54 · Mudar a data ou cancelar o pedido.** Como Operações e Compras, quero mudar a data ou cancelar o pedido com o motivo, para a previsão continuar certa.

- Motivo obrigatório; a data antiga fica na auditoria; cancelado não some, vai para a aba Cancelados.
- Pedido aberto com a data vencida aparece como atrasado.

### EP13 · Acesso (continuação)

**HU50 · "Ver como" (spec crm/10).** Como gerente, quero ver o sistema exatamente como outra pessoa da equipe vê, para tirar dúvidas e conferir permissões.

- Só gerente e equipe técnica usam; dura até 30 minutos.
- É só para olhar: qualquer alteração é recusada; entrar e sair ficam registrados.
- Uma faixa no topo mostra o tempo todo de quem é a visão.

### Adendo de 29/10/2026 (fim do dia)

**HU55 · Etapas do pedido e nota fiscal.** Como Operações e Compras, quero saber se o pedido saiu do fornecedor, se chegou e qual nota deu entrada, para cobrar o fornecedor e conferir a mercadoria.

- Pedido feito → em trânsito (eu marco, com a nota se já veio) → em conferência (quem recebe marca) → entrada feita (com a nota fiscal, obrigatória).
- Depois que chegou, a data não muda; cancelar vale até a entrada; cada etapa fica na auditoria.
- Na conferência conto peça por peça: chegou a menos, devolvo a nota (nada entra); chegou a mais, o extra entra com a nota de bonificação.

**HU56 · Estoque no Dashboard.** Como Operações e Compras, quero abrir o Dashboard e ver o estoque, para saber na hora o que precisa de atenção.

- Peças e valor em estoque, zerados, acabam em 7 dias, para pedir, pedidos feitos, em trânsito, em conferência e notas que deram entrada.
- Eu não vejo vendas nem faturamento; o gerente vê vendas e estoque.

**HU57 · Talita do cliente e pedido de peça.** Como cliente, quero perguntar sobre peças e meus pedidos e pedir uma peça que não está na vitrine, para comprar o que procuro.

- A Talita do cliente só fala da vitrine, dos meus pedidos e das peças que pedi; nada da operação da loja.
- Pedir peça (pela Talita, depois de confirmar, ou pelo botão da vitrine) avisa o gerente e o estoque; acompanho em Meus pedidos.
