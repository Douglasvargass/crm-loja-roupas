## 10. Apresentação e decisões

O roteiro segue os 5 passos sugeridos no enunciado e acrescenta o controle de acesso e 4 provas de robustez. É isso que mostra que o estoque e o histórico ficam coerentes.

### Roteiro (cerca de 10 minutos)

1. **Acesso:** entrar como Gerente e mostrar que o Vendedor não vê o Dashboard nem o botão Cancelar (HU33, RN24).
2. **Cadastros:** criar a cliente "Gabriela Martins", a categoria "Shorts" e o produto "Short Jeans 38" com estoque inicial 6 (HU01, HU07, HU09).
3. **Estoque:** mostrar o saldo, lançar entrada de 4 unidades e abrir o histórico com as duas movimentações (HU16, HU17, HU20).
4. **Venda:** abrir venda para Gabriela, incluir 2 Short Jeans + 1 Cinto de Couro, mostrar subtotais e total (HU21, HU22).
5. **Confirmação:** confirmar, mostrar o estoque baixado e as saídas no histórico (HU23).
6. **Histórico e dashboard:** abrir a ficha da Gabriela e o dashboard atualizado sozinho (HU06, HU27–HU30).
7. **Prova 1, estoque insuficiente:** tentar vender 50 unidades e mostrar a frase de bloqueio (CT22).
8. **Prova 2, cancelamento:** cancelar a venda do passo 5 e mostrar o estoque devolvido e o dashboard recalculado (CT11, CT12).
9. **Prova 3, preço congelado:** alterar o preço do Short Jeans e reabrir a venda antiga (CT30).
10. **Prova 4, qualidade:** mostrar o CI verde no último PR, com os testes de integração rodando contra Postgres real.

### Decisões tomadas

| Ponto | Decisão | Motivo |
| --- | --- | --- |
| Arquitetura | Monólito modular em Java | Monólito modular, migrations versionadas e testes contra banco real, já provados em produção |
| Stack | Spring Boot 3 + PostgreSQL 16 + React 19 | Java exigido no curso; Postgres e React por serem padrão de mercado |
| Schema | Flyway; Hibernate só valida | Schema versionado e revisável; o Hibernate nunca altera tabelas |
| Idioma | Código em inglês, tela e documentação em português | Convenção da equipe; enums também em inglês, traduzidos no `adapters.ts` |
| Estoque baixo | `min_stock` por produto, padrão 5 | Com padrão 0 o alerta nunca dispara |
| Preço | `> 0` | Produto a R$ 0,00 é erro de cadastro; o enunciado só exige "não negativo" |
| Tamanho e cor | Cada combinação é um produto com código próprio | Atende o enunciado sem tabela de variações |
| Cliente na venda | Opcional | O texto diz "quando identificado" |
| Quando baixar estoque | Só na confirmação, com trava de linha | Carrinho não reserva estoque; nada de baixa dupla |
| Excluir com vínculo | Bloquear (409) e oferecer inativar | Entidade com histórico nunca é apagada |
| Venda | Nunca excluída; cancelada com motivo | Preserva o histórico e a auditoria |
| Login | JWT em cookie httpOnly + refresh rotativo | Token nunca fica no navegador |
| Permissões | Módulo `crm` com 3 papéis semeados; sem escopo por loja | Uma loja só; escopo por unidade não se aplica |
| Front | Proxy same-origin do Vite em `/api` | Cookie `sameSite=strict` funciona sem CORS |

### Fluxo de trabalho (SDD + Git)

1. Cada entrega começa por uma spec em `crm-loja-docs/specs/crm/NN-nome/`: `requirements.md` (REQ-01..N, o quê e por quê), `design.md` (arquivos, classes, testes) e `tasks.md` (checklist com os portões).
2. Specs planejadas: `01-acesso`, `02-cadastros`, `03-estoque`, `04-vendas`, `05-dashboard`.
3. ADRs em `docs/decisions/`: 0001 arquitetura modular em Java, 0002 PostgreSQL + Flyway, 0003 autenticação por cookie, 0004 prefixo único `/api`.
4. Branch por tarefa a partir da `develop` (`feat/`, `fix/`, `docs/`) → commit em Conventional Commits em português → PR para `develop` → CI verde → merge sem apagar a branch.
5. Back-end antes do front quando a tela depende de rota nova.
6. `develop` → `main` só na entrega final ao professor.
7. A última task de cada spec é o resumo em `docs/implementacoes/`.

### Observações sobre o enunciado

- O critério "Relacionamentos" cita "animais e tutores": o documento é compartilhado com o projeto da outra equipe. Para este projeto vale "vendas e produtos".
- A numeração pula de "6. Dashboard" para "3. Critérios gerais"; é só formatação.
