import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSession } from '@/app/context/SessionContext';
import { http } from '@/data/http';
import type { Role } from '@/data/dto';
import { cn } from '@/lib/cn';
import { Button } from '../components/atoms/Button';
import { Markdown } from '../components/molecules/Markdown';
import { Card, PageHeader } from '../components/templates/Page';

interface Status { enabled: boolean; provider: string | null; model: string | null }

const PROVIDER_LABEL: Record<string, string> = {
  anthropic: 'Claude (Anthropic)',
  groq: 'Groq',
  gemini: 'Google Gemini',
  openrouter: 'OpenRouter',
  ollama: 'Ollama (local)',
  openai: 'provedor compatível',
};
interface Reply { conversationId: string; answer: string; toolsUsed: string[] }
type Msg = { from: 'user' | 'assistant' | 'error'; text: string; tools?: string[] };

const TOOL_LABEL: Record<string, string> = {
  consultar_dashboard: 'indicadores',
  buscar_produtos: 'produtos',
  detalhe_produto: 'ficha do produto',
  listar_categorias: 'categorias',
  buscar_clientes: 'clientes',
  detalhe_cliente: 'ficha do cliente',
  buscar_vendas: 'vendas',
  detalhe_venda: 'detalhe da venda',
  alertas_estoque: 'alertas de estoque',
  movimentacoes_estoque: 'movimentações',
};

const SUGGESTIONS: Record<Role, string[]> = {
  MANAGER: [
    'Quanto vendemos este mês e como foi em relação ao mês passado?',
    'Quais produtos preciso repor com urgência?',
    'Quais os 5 produtos mais vendidos nos últimos 30 dias?',
    'Qual categoria fatura mais?',
  ],
  SELLER: [
    'Tem camiseta tamanho M disponível?',
    'O que a cliente Ana Paula já comprou?',
    'Como cancelo uma venda?',
    'Quais vendas ficaram abertas sem confirmar?',
  ],
  STOCK_KEEPER: [
    'O que está sem estoque?',
    'Quais produtos estão abaixo do mínimo?',
    'Quais foram as últimas entradas de estoque?',
    'Como registro uma avaria?',
  ],
};

export function AssistantPage() {
  const { role } = useSession();
  const status = useQuery({ queryKey: ['assistantStatus'], queryFn: () => http.get<Status>('/crm/assistant/status') });
  const [messages, setMessages] = useState<Msg[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  // Chaves obrigatórias: em navegadores novos scrollIntoView devolve uma Promise, e o React
  // a trataria como função de limpeza ("destroy is not a function" ao sair da página).
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, busy]);

  async function send(text: string) {
    const question = text.trim();
    if (!question || busy) return;
    setInput('');
    setMessages((m) => [...m, { from: 'user', text: question }]);
    setBusy(true);
    try {
      const r = await http.post<Reply>('/crm/assistant/chat', { conversationId, message: question });
      setConversationId(r.conversationId);
      setMessages((m) => [...m, { from: 'assistant', text: r.answer, tools: r.toolsUsed }]);
    } catch (err) {
      setMessages((m) => [...m, { from: 'error', text: err instanceof Error ? err.message : 'Erro ao falar com o assistente.' }]);
    } finally {
      setBusy(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void send(input);
  }

  const disabled = status.data && !status.data.enabled;

  return (
    <>
      <PageHeader
        title="Assistente"
        subtitle="Pergunte sobre vendas, estoque, clientes e como usar o sistema. As respostas usam os dados reais da loja."
        actions={messages.length > 0 && (
          <Button variant="secondary" onClick={() => { setMessages([]); setConversationId(null); }}>Nova conversa</Button>
        )}
      />

      {disabled && (
        <div role="alert" className="mb-4 rounded-lg border border-warning/40 bg-warning-soft px-4 py-3 text-sm text-warning">
          O assistente está desligado neste servidor. Para ligar, configure um provedor de IA no back-end
          (<code>ASSISTANT_PROVIDER</code> e <code>ASSISTANT_API_KEY</code>). Há opções grátis, como Groq e Google Gemini:
          veja docs/10-assistente-ia.md.
        </div>
      )}

      <Card className="flex min-h-[60vh] flex-col">
        <div className="flex-1 space-y-4 overflow-y-auto p-4">
          {messages.length === 0 && role && (
            <div className="py-6 text-center">
              <p className="text-sm text-muted">Experimente perguntar:</p>
              <div className="mt-3 flex flex-wrap justify-center gap-2">
                {SUGGESTIONS[role].map((s) => (
                  <button key={s} type="button" disabled={!!disabled || busy} onClick={() => void send(s)}
                    className="rounded-full border border-border bg-surface px-3 py-1.5 text-sm hover:bg-surface-2 disabled:opacity-50">
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}
          {messages.map((m, i) => (
            <div key={i} className={cn('flex', m.from === 'user' ? 'justify-end' : 'justify-start')}>
              <div
                className={cn(
                  'max-w-[85%] rounded-2xl px-4 py-2.5',
                  m.from === 'user' && 'bg-primary text-on-primary',
                  m.from === 'assistant' && 'border border-border bg-surface-2',
                  m.from === 'error' && 'bg-error-soft text-error text-sm',
                )}
              >
                {m.from === 'assistant' ? <Markdown text={m.text} /> : <p className="text-sm whitespace-pre-wrap">{m.text}</p>}
                {m.tools && m.tools.length > 0 && (
                  <p className="mt-2 text-xs text-muted">Consultei: {m.tools.map((t) => TOOL_LABEL[t] ?? t).join(', ')}</p>
                )}
              </div>
            </div>
          ))}
          {busy && (
            <div className="flex justify-start">
              <div className="rounded-2xl border border-border bg-surface-2 px-4 py-2.5 text-sm text-muted" aria-live="polite">
                Consultando os dados da loja…
              </div>
            </div>
          )}
          <div ref={endRef} />
        </div>
        <form onSubmit={submit} className="flex gap-2 border-t border-border p-3">
          <label htmlFor="assistant-input" className="sr-only">Sua pergunta</label>
          <textarea
            id="assistant-input"
            rows={1}
            value={input}
            maxLength={2000}
            disabled={!!disabled}
            placeholder="Ex.: quanto vendemos ontem?"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                void send(input);
              }
            }}
            className="min-h-10 flex-1 resize-none rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-primary focus:ring-2 focus:ring-primary/25 focus:outline-none"
          />
          <Button type="submit" loading={busy} disabled={!!disabled || !input.trim()}>Enviar</Button>
        </form>
      </Card>
      <p className="mt-2 text-xs text-muted">
        O assistente só consulta: não cadastra, não vende e não altera estoque. Ele pode errar; confira números importantes nas telas.
        {status.data?.enabled && status.data.provider && (
          <> · IA: {PROVIDER_LABEL[status.data.provider] ?? status.data.provider}{status.data.model ? ` (${status.data.model})` : ''}</>
        )}
      </p>
    </>
  );
}
