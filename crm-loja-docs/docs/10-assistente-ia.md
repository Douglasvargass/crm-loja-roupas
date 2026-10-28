# Assistente de IA

O menu **Assistente IA** abre um chat onde qualquer pessoa da equipe pergunta sobre a loja em linguagem natural:

- "Quanto vendemos este mês? E comparado com o mês passado?"
- "Quais produtos preciso repor com urgência?"
- "Tem calça 40 disponível?"
- "O que a cliente Ana Paula já comprou?"
- "Como cancelo uma venda?"

As respostas usam os **dados reais do banco no momento da pergunta**. O assistente nunca inventa números: se não tiver acesso a uma informação, ele diz isso.

## Como ligar

O assistente usa o Claude, da Anthropic, e precisa de uma chave de API.

1. Crie uma conta em https://console.anthropic.com e gere uma chave em *API Keys*. Ela começa com `sk-ant-`.
2. Rode o back-end com a chave na variável `ANTHROPIC_API_KEY`:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:test-run
```

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."; .\mvnw.cmd spring-boot:test-run
```

3. Abra **Assistente IA** no menu. O aviso amarelo de "assistente desligado" some.

> **Nunca coloque a chave no código, no `application.yml` ou no Git.** Ela fica só na variável de ambiente (ou no `.env`, que o `.gitignore` já exclui). Uma chave publicada no GitHub pode ser usada por qualquer pessoa e gerar cobrança.

Sem a chave, o restante do sistema funciona normalmente; só o assistente fica desligado.

## O que ele sabe

| Fonte | Conteúdo |
| --- | --- |
| Instruções fixas (`AssistantPrompt.java`) | Regras da loja (CPF, estoque, ciclo da venda, cancelamento…), o que cada papel faz e onde fica cada função na tela |
| Consultas ao banco (`AssistantTools.java`) | Dados vivos: vendas, produtos, estoque, clientes, indicadores |

As 10 consultas, todas **somente leitura**:

| Consulta | O que traz | Perfis |
| --- | --- | --- |
| `consultar_dashboard` | Indicadores de um período, comparação, mais vendidos, por categoria | Gerente |
| `buscar_produtos` | Produtos por nome, código, categoria, tamanho e situação | Todos |
| `detalhe_produto` | Ficha, últimas movimentações e total vendido | Todos |
| `listar_categorias` | Categorias e quantidade de produtos | Todos |
| `buscar_clientes` | Clientes por nome ou CPF | Gerente, Vendedor |
| `detalhe_cliente` | Ficha, resumo e histórico de compras | Gerente, Vendedor |
| `buscar_vendas` | Vendas por número, cliente, período e status | Gerente, Vendedor |
| `detalhe_venda` | Itens, preços praticados, cancelamento | Gerente, Vendedor |
| `alertas_estoque` | Estoque baixo e zerado | Gerente, Estoquista |
| `movimentacoes_estoque` | Histórico de entradas, ajustes, vendas e estornos | Gerente, Estoquista |

## Segurança

- **Mesmas permissões da tela.** O back-end só entrega ao Claude as consultas que o perfil de quem pergunta pode ver, e confere de novo antes de executar. Um vendedor que pergunta o faturamento recebe "seu perfil não tem acesso".
- **Só leitura.** Nenhuma consulta cadastra, vende ou altera estoque. Quando pedem uma ação, o assistente explica onde fazer na tela.
- **Dados não são instruções.** O texto que volta do banco (nomes, observações) é tratado como informação, não como comando para o modelo.
- **Conversas na memória do servidor.** Cada conversa pertence a um usuário, some após 2 horas sem uso e não é gravada no banco. Reiniciar o back-end apaga todas.

## Como funciona

```
Tela (AssistantPage)  --POST /api/crm/assistant/chat-->  AssistantController
                                                            │
                                                     AssistantService
                                                            │  1. envia pergunta + histórico + consultas permitidas
                                                            ▼
                                                    API do Claude (Anthropic)
                                                            │  2. Claude pede uma ou mais consultas
                                                            ▼
                                              AssistantTools → Services → PostgreSQL
                                                            │  3. resultados em JSON voltam ao Claude
                                                            ▼
                                                  (repete até 8 rodadas)
                                                            │  4. resposta final em texto
                                                            ▼
                                     Tela mostra a resposta + "Consultei: vendas, produtos…"
```

| Arquivo | Papel |
| --- | --- |
| `crm-loja-backend/.../assistant/AssistantService.java` | Laço de conversa com a API do Claude, histórico, erros |
| `crm-loja-backend/.../assistant/AssistantTools.java` | As consultas, com descrição, parâmetros e papéis |
| `crm-loja-backend/.../assistant/AssistantPrompt.java` | O "conhecimento da empresa": regras e funcionamento |
| `crm-loja-backend/.../assistant/AssistantController.java` | `GET /crm/assistant/status` e `POST /crm/assistant/chat` |
| `crm-loja-frontend/src/presentation/pages/AssistantPage.tsx` | O chat |

Configurações (variáveis de ambiente):

| Variável | Padrão | Observação |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | vazio | Liga o assistente |
| `ASSISTANT_MODEL` | `claude-opus-5-5` | Modelo usado |
| `ASSISTANT_EFFORT` | `medium` | `low` responde mais rápido e gasta menos; `high` pensa mais em perguntas difíceis |
| `ASSISTANT_MAX_TOKENS` | `16000` | Teto de tokens por resposta. Abaixe (ex.: `4000`) se o provedor recusar por falta de créditos (erro 402) |
| `ANTHROPIC_BASE_URL` | vazio | Vazio = API da Anthropic. Preencha para usar um provedor compatível, como o OpenRouter |

O assistente também ativa o *fallback* do servidor da Anthropic (`fallbacks: "default"`): se um filtro de segurança recusar uma pergunta legítima por engano, a própria API tenta de novo com outro modelo. Com `ANTHROPIC_BASE_URL` preenchido esse recurso fica desligado, porque só existe na API da Anthropic.

### Usar uma chave do OpenRouter

O [OpenRouter](https://openrouter.ai) dá acesso aos modelos Claude com uma API compatível. Chaves dele começam com `sk-or-` (as da Anthropic, com `sk-ant-`). No OpenRouter o nome do modelo é diferente:

```powershell
$env:ANTHROPIC_API_KEY  = "sk-or-v1-..."
$env:ANTHROPIC_BASE_URL = "https://openrouter.ai/api"
$env:ASSISTANT_MODEL    = "anthropic/claude-opus-5.5"   # ou anthropic/claude-haiku-5.5, mais barato
.\mvnw.cmd spring-boot:test-run
```

Conta gratuita do OpenRouter tem saldo pequeno: use `anthropic/claude-haiku-5.5` com `ASSISTANT_MAX_TOKENS=4000`. Sem créditos, a tela mostra "A conta da chave do assistente está sem créditos".

## Custo

Cada pergunta é cobrada pela Anthropic conforme o tamanho do texto enviado e recebido, incluindo os dados consultados. Para acompanhar e limitar o gasto, use o painel em https://console.anthropic.com (*Usage* e *Limits*). Para gastar menos: `ASSISTANT_EFFORT=low` e perguntas mais específicas ("vendas de ontem" em vez de "todas as vendas").

## Ensinar algo novo ao assistente

**Uma regra ou informação da empresa** (ex.: política de trocas, horário da loja): edite o texto em `AssistantPrompt.java`. Escreva como explicaria a um funcionário novo.

**Uma consulta nova** (ex.: "vendas por vendedor"):

1. Garanta que existe um método de Service que traz o dado.
2. Em `AssistantTools.java`, adicione um `add(new Def(...))` no construtor com:
   - `name`: em snake_case, ex.: `vendas_por_vendedor`;
   - `description`: o que traz e quando usar. É isso que o Claude lê para decidir;
   - os papéis que podem ver;
   - os parâmetros (`str`, `integer`, `date`, `enumOf`) e quais são obrigatórios;
   - a função que chama o Service.
3. Em `AssistantIT.java`, ajuste a contagem (`hasSize(10)`) e teste a consulta nova.
4. Em `AssistantPage.tsx`, adicione o nome amigável em `TOOL_LABEL`.

## Testar

- **Automático, sem chave:** `./mvnw verify -Dtest=CpfTest -Dit.test=AssistantIT`. Testa o modo desligado, as permissões por papel e se as consultas devolvem dados reais.
- **Manual, com chave:** entre com cada perfil e faça as perguntas sugeridas na tela. Confira os números nas telas (Dashboard, Estoque, Vendas). Roteiro na seção 9 de [12-roteiro-de-testes.md](12-roteiro-de-testes.md).
