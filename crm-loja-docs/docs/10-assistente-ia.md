# Assistente de IA

O menu **Assistente IA** abre um chat onde qualquer pessoa da equipe pergunta sobre a loja em linguagem natural:

- "Quanto vendemos este mês? E comparado com o mês passado?"
- "Quais produtos preciso repor com urgência?"
- "Tem calça 40 disponível?"
- "O que a cliente Ana Paula já comprou?"
- "Como cancelo uma venda?"

As respostas usam os **dados reais do banco no momento da pergunta**. O assistente nunca inventa números: se não tiver acesso a uma informação, ele diz isso.

## Como ligar

O assistente precisa de um **provedor de IA** e de uma **chave de API** dele. Você escolhe o provedor com duas variáveis de ambiente, `ASSISTANT_PROVIDER` e `ASSISTANT_API_KEY`. Sem elas, o restante do sistema funciona normalmente; só o assistente fica desligado.

### Opções grátis (sem cartão)

| Provedor | `ASSISTANT_PROVIDER` | Onde pegar a chave | Modelo padrão | Limite grátis (aprox.) | Observações |
| --- | --- | --- | --- | --- | --- |
| **Groq** (recomendado) | `groq` | https://console.groq.com → *API Keys* → *Create API Key* (começa com `gsk_`) | `openai/gpt-oss-120b` | Dezenas de perguntas por dia; o teto é de tokens por dia e por minuto, veja https://console.groq.com/settings/limits | Rápido, bom com as consultas. Se bater o limite, a tela avisa "limite gratuito atingido"; espere um minuto ou troque `ASSISTANT_MODEL` para `openai/gpt-oss-20b` (mais leve) ou `qwen/qwen3.8-27b`. O catálogo do Groq muda: se aparecer "modelo não existe", liste os atuais com `curl https://api.groq.com/openai/v1/models -H "Authorization: Bearer $CHAVE"` |
| **Google Gemini** | `gemini` | https://aistudio.google.com/apikey → *Create API key* | `gemini-2.5-flash` | ~250 perguntas por dia | Ótima qualidade. No plano grátis, o Google pode usar as conversas para treinar modelos |
| **OpenRouter** | `openrouter` | https://openrouter.ai/keys (começa com `sk-or-`) | `meta-llama/llama-3.3-70b-instruct:free` | ~50 pedidos por dia nos modelos `:free` | Modelos grátis mudam e às vezes ficam fora do ar; só os com sufixo `:free` são grátis |
| **Ollama** (na sua máquina) | `ollama` | não precisa de chave | `llama3.1` | Sem limite | Precisa do [Ollama](https://ollama.com) instalado e `ollama pull llama3.1`. Não serve para o Render (sem GPU, pouca memória) |

Os limites mudam com frequência e variam por modelo: confira na página do provedor. Cada **pergunta** gasta de 1 a 4 pedidos (um por consulta ao banco) e alguns milhares de tokens, porque a conversa inteira vai de novo a cada rodada.

### Opção paga

| Provedor | `ASSISTANT_PROVIDER` | Chave | Modelo padrão |
| --- | --- | --- | --- |
| Anthropic (Claude) | `anthropic` (padrão) | https://console.anthropic.com → *API Keys* (começa com `sk-ant-`); precisa de crédito em *Billing* | `claude-opus-5-5` |

### Passo a passo

**No seu computador:**

```powershell
$env:ASSISTANT_PROVIDER = "groq"
$env:ASSISTANT_API_KEY  = "gsk_..."
.\mvnw.cmd spring-boot:test-run
```

```bash
ASSISTANT_PROVIDER=groq ASSISTANT_API_KEY=gsk_... ./mvnw spring-boot:test-run
```

**No Render (sistema online):** painel → serviço `crm-loja` → **Environment** → *Add Environment Variable*:

| Nome | Valor |
| --- | --- |
| `ASSISTANT_PROVIDER` | `groq` |
| `ASSISTANT_API_KEY` | a chave `gsk_...` |

Clique em **Save Changes**; o serviço reinicia em alguns minutos. Depois abra **Assistente IA** no menu: o aviso amarelo some e, no rodapé do chat, aparece "IA: Groq (openai/gpt-oss-120b)".

Para trocar de provedor, mude as duas variáveis. Para outro modelo do mesmo provedor, acrescente `ASSISTANT_MODEL`.

> **Nunca coloque a chave no código, no `application.yml` ou no Git.** Ela fica só na variável de ambiente (ou no `.env`, que o `.gitignore` já exclui). Uma chave publicada no GitHub pode ser usada por qualquer pessoa.

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
| `crm-loja-backend/.../assistant/AssistantService.java` | Conversas na memória, papel de quem pergunta, rollback em erro |
| `crm-loja-backend/.../assistant/LlmProvider.java` | Interface entre o Service e a API de IA |
| `crm-loja-backend/.../assistant/AnthropicProvider.java` | Laço de ferramentas pela API da Anthropic (SDK) |
| `crm-loja-backend/.../assistant/OpenAiCompatibleProvider.java` | Laço de ferramentas pelo formato OpenAI-compatível (Groq, Gemini, OpenRouter, Ollama) |
| `crm-loja-backend/.../assistant/AssistantTools.java` | As consultas, com descrição, parâmetros e papéis |
| `crm-loja-backend/.../assistant/AssistantPrompt.java` | O "conhecimento da empresa": regras e funcionamento |
| `crm-loja-backend/.../assistant/AssistantController.java` | `GET /crm/assistant/status` e `POST /crm/assistant/chat` |
| `crm-loja-frontend/src/presentation/pages/AssistantPage.tsx` | O chat |

Configurações (variáveis de ambiente):

| Variável | Padrão | Observação |
| --- | --- | --- |
| `ASSISTANT_PROVIDER` | `anthropic` | `anthropic`, `groq`, `gemini`, `openrouter`, `ollama` ou `openai` (outro provedor OpenAI-compatível) |
| `ASSISTANT_API_KEY` | vazio | Liga o assistente (menos no `ollama`). `ANTHROPIC_API_KEY` continua aceita como sinônimo |
| `ASSISTANT_MODEL` | padrão do provedor | `claude-opus-5-5`, `openai/gpt-oss-120b`, `gemini-2.5-flash`, `meta-llama/llama-3.3-70b-instruct:free`, `llama3.1` |
| `ASSISTANT_BASE_URL` | padrão do provedor | Obrigatória só no `openai`. No `anthropic`, aponta para um provedor compatível com a API da Anthropic (ex.: `https://openrouter.ai/api`) |
| `ASSISTANT_EFFORT` | `medium` | Só na Anthropic: `low` responde mais rápido e gasta menos; `high` pensa mais |
| `ASSISTANT_MAX_TOKENS` | `16000` | Teto de tokens por resposta. Abaixe (ex.: `4000`) nos planos grátis ou se o provedor recusar por falta de créditos (erro 402) |

Dois formatos de API são suportados, e o provedor escolhe qual:

- **Anthropic** (`AnthropicProvider.java`): SDK oficial. Ativa o *fallback* do servidor (`fallbacks: "default"`): se um filtro de segurança recusar uma pergunta legítima por engano, a própria API tenta de novo com outro modelo. Com `ASSISTANT_BASE_URL` preenchido (OpenRouter em `/api`), esse recurso fica desligado, porque só existe na API da Anthropic.
- **OpenAI-compatível** (`OpenAiCompatibleProvider.java`): `POST {base}/chat/completions` com *function calling*, sem SDK. É o formato de Groq, Gemini, OpenRouter, Ollama e da maioria dos outros provedores. Mensagens de erro: 401/403 → chave inválida; 402 → sem créditos; 404 → modelo não existe; 429 → limite grátis atingido.

### Usar uma chave do OpenRouter com modelos Claude (pago)

Chaves do OpenRouter começam com `sk-or-`. Para os modelos Claude pelo OpenRouter, use o formato da Anthropic:

```powershell
$env:ASSISTANT_PROVIDER = "anthropic"
$env:ASSISTANT_API_KEY  = "sk-or-v1-..."
$env:ASSISTANT_BASE_URL = "https://openrouter.ai/api"
$env:ASSISTANT_MODEL    = "anthropic/claude-haiku-5.5"
.\mvnw.cmd spring-boot:test-run
```

Para os modelos **grátis** do OpenRouter, use `ASSISTANT_PROVIDER=openrouter` (tabela acima). Sem créditos, a tela mostra "A conta da chave do assistente está sem créditos".

## Custo

- **Planos grátis (Groq, Gemini, OpenRouter `:free`):** custo zero, com limite de pedidos e tokens por dia. Passou do limite, a tela diz "limite gratuito atingido" e volta a funcionar quando o contador zera (em geral, 1 minuto ou no dia seguinte).
- **Anthropic:** cada pergunta é cobrada conforme o texto enviado e recebido, incluindo os dados consultados. Acompanhe e limite o gasto em https://console.anthropic.com (*Usage* e *Limits*).

Para gastar menos em qualquer provedor: `ASSISTANT_MAX_TOKENS=4000` e perguntas específicas ("vendas de ontem" em vez de "todas as vendas").

> **Atenção com a demonstração pública:** os logins de demonstração (`loja12345`) são públicos. Quem entrar com eles usa a *sua* cota (ou o seu crédito). Nos planos grátis o risco é só esgotar a cota do dia; na Anthropic, defina um limite mensal no painel.

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

- **Automático, sem chave:** `./mvnw verify -Dtest='CpfTest,AssistantPropertiesTest,OpenAiCompatibleProviderTest' -Dit.test=AssistantIT`. Testa o modo desligado, as permissões por papel, se as consultas devolvem dados reais e, com um servidor simulado, o formato dos pedidos e o laço de ferramentas do provedor OpenAI-compatível.
- **Manual, com chave:** entre com cada perfil e faça as perguntas sugeridas na tela. Confira os números nas telas (Dashboard, Estoque, Vendas). Roteiro na seção 9 de [12-roteiro-de-testes.md](12-roteiro-de-testes.md).
