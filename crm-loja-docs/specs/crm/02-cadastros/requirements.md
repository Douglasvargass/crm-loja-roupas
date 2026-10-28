# 02-cadastros · Cadastros: clientes, categorias e produtos

## Contexto

Parte do MVP de CRM da loja de roupas (enunciado "Mini Projeto SENAI – Loja"). Histórias e regras completas em docs/03-historias-de-usuario.md e docs/04-regras-de-negocio.md.

## Requisitos

### REQ-01 · HU01 · Cadastrar cliente

Como vendedor, quero cadastrar um cliente com nome, CPF, telefone, e-mail e endereço, para identificá-lo nas vendas.

- Dado o formulário preenchido corretamente, quando salvo, então o cliente aparece na lista e vejo "Cliente cadastrado com sucesso".
- Dado um campo obrigatório vazio, quando salvo, então o campo fica destacado e nada é gravado.
- Dado um CPF com dígito verificador inválido, quando salvo, então vejo "CPF inválido".
- Dado um CPF já cadastrado, quando salvo, então vejo "Já existe cliente com este CPF".
- Dado um e-mail fora do formato, quando salvo, então vejo "E-mail inválido".

### REQ-02 · HU02 · Listar e pesquisar clientes

Como vendedor, quero ver a lista e buscar por nome ou CPF, para achar o cliente no atendimento.

- A lista mostra nome, CPF formatado, telefone e situação, paginada de 10 em 10.
- A busca por nome é parcial e ignora maiúsculas e acentos ("joao" encontra "João").
- A busca por CPF aceita com ou sem pontuação.
- Sem resultados, aparece "Nenhum cliente encontrado".

### REQ-03 · HU03 · Ver detalhes do cliente

Como vendedor, quero abrir a ficha completa do cliente, para conferir dados e compras.

- A ficha mostra todos os campos, a data de cadastro e o resumo de compras (quantidade e valor total confirmado).

### REQ-04 · HU04 · Editar cliente

Como vendedor, quero alterar os dados do cliente, para mantê-los atualizados.

- Valem as mesmas validações do cadastro.
- Alterar para um CPF de outro cliente é recusado.
- As vendas antigas continuam vinculadas ao cliente.

### REQ-05 · HU05 · Excluir ou inativar cliente

Como gerente, quero remover clientes sem perder histórico, para manter a base limpa.

- Dado um cliente sem vendas, quando excluo e confirmo, então ele é apagado.
- Dado um cliente com vendas, quando tento excluir, então vejo "Cliente possui 3 venda(s) e não pode ser excluído. Deseja inativá-lo?".
- Cliente inativo não aparece para novas vendas, mas continua no histórico.

### REQ-06 · HU06 · Ver histórico de compras

Como vendedor, quero ver as compras do cliente, para atender melhor e sugerir produtos.

- Lista número, data, quantidade de itens, total e status de cada venda, da mais recente para a mais antiga.
- Clicar numa venda abre o detalhe (HU26).

### REQ-07 · HU07 · Cadastrar categoria

Como gerente, quero cadastrar categorias (camisetas, calças, vestidos, jaquetas, acessórios), para organizar os produtos.

- Nome obrigatório e único, sem diferenciar maiúsculas ("Calças" = "calças").

### REQ-08 · HU08 · Listar, editar e excluir categoria

Como gerente, quero manter as categorias, para corrigir e reorganizar.

- A lista mostra quantos produtos cada categoria tem.
- Categoria com produtos não pode ser excluída: "Categoria possui 12 produto(s) vinculados".
- Categoria inativa não aparece no cadastro de novos produtos.

### REQ-09 · HU09 · Cadastrar produto

Como estoquista, quero cadastrar um produto com nome, descrição, categoria, tamanho, cor, código, preço e estoque inicial, para colocá-lo à venda.

- Código único; duplicado mostra "Código já utilizado".
- Preço deve ser maior que zero; estoque inicial e estoque mínimo, maiores ou iguais a zero.
- Estoque inicial maior que zero gera uma movimentação `ENTRY` com a observação "Estoque inicial".

### REQ-10 · HU10 · Listar e pesquisar produtos

Como vendedor, quero buscar por nome, categoria, tamanho ou código, para encontrar a peça rápido.

- Os filtros combinam entre si (ex.: categoria "Calças" + tamanho "40").
- A lista mostra código, nome, categoria, tamanho, cor, preço, estoque e situação.

### REQ-11 · HU11 · Ver detalhes do produto

Como vendedor, quero ver a ficha do produto, para informar o cliente.

- Mostra todos os campos, as últimas movimentações e o total vendido.

### REQ-12 · HU12 · Editar produto

Como estoquista, quero alterar dados do produto, para corrigir informações e preço.

- O estoque **não** é editável aqui; muda só por entrada, ajuste ou venda.
- Mudar o preço não altera vendas já registradas.

### REQ-13 · HU13 · Excluir ou inativar produto

Como gerente, quero remover produtos sem quebrar o histórico.

- Produto sem vendas e sem movimentações (além da inicial) pode ser excluído.
- Produto com vínculos só pode ser inativado.

### REQ-14 · HU14 · Identificar ativos e indisponíveis

Como vendedor, quero ver claramente o que posso vender.

- Etiquetas: **Disponível** (ativo e estoque > 0), **Sem estoque** (ativo e estoque = 0), **Inativo**.
- Filtro por situação: todos, disponíveis, sem estoque, estoque baixo, inativos.

### REQ-15 · HU15 · Filtrar por categoria

Como vendedor, quero filtrar a lista por categoria, para ver só um tipo de peça.

## Regras de negócio envolvidas

- RN01: CPF obrigatório, único, 11 dígitos, dígito verificador válido
- RN02: Cliente com vendas não é excluído; só inativado
- RN03: Nome de categoria único, sem diferenciar maiúsculas
- RN04: Categoria com produtos não é excluída
- RN05: Código de produto único
- RN06: Preço > 0; estoque e estoque mínimo ≥ 0
- RN07: Produto com vendas ou movimentações além da inicial não é excluído; só inativado

## Fora de escopo

- Escopo por loja ou região (uma loja só).
- Descontos, formas de pagamento, nota fiscal, troca e devolução parcial.
