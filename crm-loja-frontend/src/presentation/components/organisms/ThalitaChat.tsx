import { useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSession } from '@/app/context/SessionContext';
import { http } from '@/data/http';
import type { Role } from '@/data/dto';
import { cn } from '@/lib/cn';
import { Icon } from '../atoms/Icon';
import { ThalitaAvatar } from '../atoms/ThalitaAvatar';
import { Markdown } from '../molecules/Markdown';

interface Status { enabled: boolean; provider: string | null; model: string | null }
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
  meu_painel: 'seu painel',
  metas_vendedores: 'metas',
};

const MANAGER_SUGGESTIONS = [
  'Quanto vendemos este mês?',
  'Quem está mais longe da meta?',
  'O que preciso repor com urgência?',
  'Quais os 5 produtos mais vendidos?',
];

const SUGGESTIONS: Record<Role, string[]> = {
  // Thalita do cliente (29/10/2026): só peças da vitrine, os pedidos dele e pedir uma peça que a loja não tem.
  CUSTOMER: [
    'Tem vestido tamanho M?',
    'Como está o meu último pedido?',
    'Procuro uma peça que não achei na vitrine',
    'Como funciona o pagamento?',
  ],
  MANAGER: MANAGER_SUGGESTIONS,
  // Equipe técnica vê o sistema como gerente.
  DEVELOPER: MANAGER_SUGGESTIONS,
  MARKETING_DEV: MANAGER_SUGGESTIONS,
  SELLER: [
    'Quanto falta para a minha meta?',
    'Tem camiseta tamanho M disponível?',
    'O que a cliente Ana Paula já comprou?',
    'Quais vendas ficaram abertas?',
  ],
  PURCHASING: [
    'Quais chamados de peça em falta estão abertos?',
    'O que está sem estoque?',
    'Quais produtos estão abaixo do mínimo?',
    'Quais foram as últimas entradas?',
  ],
  STOCK_KEEPER: [
    'O que está sem estoque?',
    'Quais produtos estão abaixo do mínimo?',
    'Quais foram as últimas entradas?',
    'Como registro uma avaria?',
  ],
};

// Thalita: assistente flutuante. Fica montada no AppShell, então a conversa continua ao trocar de tela.
const POS_KEY = 'orden.talita.pos';
const WIDE = '(min-width: 640px)';   // mesmo ponto do `sm:` do Tailwind: abaixo disso a janela é tela cheia

function loadPos(): { x: number; y: number } | null {
  try {
    const v = JSON.parse(localStorage.getItem(POS_KEY) ?? 'null') as { x?: unknown; y?: unknown } | null;
    return v && typeof v.x === 'number' && typeof v.y === 'number' ? { x: v.x, y: v.y } : null;
  } catch { return null; }
}

// Mantém a janela inteira dentro da tela, com o cabeçalho sempre ao alcance.
function clampPos(x: number, y: number, w: number, h: number) {
  return {
    x: Math.min(Math.max(8, x), Math.max(8, window.innerWidth - w - 8)),
    y: Math.min(Math.max(8, y), Math.max(8, window.innerHeight - h - 8)),
  };
}

export function ThalitaChat() {
  const { role } = useSession();
  // Cliente fala com a Thalita da loja (/loja/assistant, outras instruções e ferramentas); a equipe, com a do sistema.
  const customer = role === 'CUSTOMER';
  const base = customer ? '/loja/assistant' : '/crm/assistant';
  const status = useQuery({ queryKey: ['assistantStatus', base], queryFn: () => http.get<Status>(`${base}/status`) });
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState(false);
  const [pos, setPos] = useState<{ x: number; y: number } | null>(loadPos);
  const [dragging, setDragging] = useState(false);
  const panelRef = useRef<HTMLElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Balãozinho de boas-vindas ao carregar a página.
  useEffect(() => {
    // MODO TESTE: o balãozinho aparece a cada carregamento da página (antes: uma vez por sessão).
    const show = setTimeout(() => setHint(true), 1500);
    const hide = setTimeout(() => setHint(false), 9000);
    return () => { clearTimeout(show); clearTimeout(hide); };
  }, []);

  const dismissHint = useCallback(() => {
    setHint(false);
  }, []);

  // Chaves obrigatórias: scrollIntoView devolve Promise em navegadores novos e o React a trataria como
  // função de limpeza ("destroy is not a function" ao desmontar).
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, busy, open]);

  useEffect(() => {
    if (open) inputRef.current?.focus({ preventScroll: true });
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  // Caixa de texto cresce até 4 linhas.
  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 104)}px`;
  }, [input, open]);

  async function send(text: string) {
    const question = text.trim();
    if (!question || busy) return;
    setInput('');
    setMessages((m) => [...m, { from: 'user', text: question }]);
    setBusy(true);
    try {
      const r = await http.post<Reply>(`${base}/chat`, { conversationId, message: question });
      setConversationId(r.conversationId);
      setMessages((m) => [...m, { from: 'assistant', text: r.answer, tools: r.toolsUsed }]);
    } catch (err) {
      setMessages((m) => [...m, { from: 'error', text: err instanceof Error ? err.message : 'Não consegui falar com a IA agora.' }]);
    } finally {
      setBusy(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void send(input);
  }

  function reset() {
    setMessages([]);
    setConversationId(null);
    inputRef.current?.focus();
  }

  // Arrastar pelo cabeçalho (só em tela larga; no celular a janela é tela cheia).
  function startDrag(e: ReactPointerEvent<HTMLElement>) {
    if (!window.matchMedia(WIDE).matches || e.button !== 0) return;
    if ((e.target as HTMLElement).closest('button')) return;
    const el = panelRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const dx = e.clientX - r.left;
    const dy = e.clientY - r.top;
    setDragging(true);
    const move = (ev: PointerEvent) => setPos(clampPos(ev.clientX - dx, ev.clientY - dy, r.width, r.height));
    const end = (ev: PointerEvent) => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', end);
      window.removeEventListener('pointercancel', end);
      setDragging(false);
      const next = clampPos(ev.clientX - dx, ev.clientY - dy, r.width, r.height);
      setPos(next);
      try { localStorage.setItem(POS_KEY, JSON.stringify(next)); } catch { /* sem armazenamento: tudo bem */ }
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', end);
    window.addEventListener('pointercancel', end);
    e.preventDefault();
  }

  function resetPos() {
    setPos(null);
    try { localStorage.removeItem(POS_KEY); } catch { /* ignora */ }
  }

  // Se a tela encolher, traz a janela de volta para dentro.
  useEffect(() => {
    if (!pos || !open) return;
    const fit = () => {
      const el = panelRef.current;
      if (!el || !window.matchMedia(WIDE).matches) return;
      const r = el.getBoundingClientRect();
      const c = clampPos(r.left, r.top, r.width, r.height);
      if (Math.abs(c.x - r.left) > 1 || Math.abs(c.y - r.top) > 1) setPos(c);
    };
    fit();
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, [pos, open]);

  const disabled = status.data ? !status.data.enabled : false;
  // Bolinha de status como nos apps de conversa: verde vivo = online, cinza = desligada (sem chave de IA no servidor).
  // Verde fixo de propósito, fora da paleta da marca: o "success" da paleta é escuro e parecia marrom (pedido do Douglas, 29/10).
  const dot = disabled ? 'bg-gray-400' : 'bg-green-500';
  const statusText = disabled ? 'Thalita desligada' : 'Thalita online';

  return (
    <>
      {open && (
        <section
          ref={panelRef}
          role="dialog"
          aria-label="Conversa com a Thalita"
          className={cn(
            'talita-pop fixed z-50 flex flex-col overflow-hidden bg-surface text-text shadow-2xl',
            // celular: tela cheia; a partir de sm: janelinha no canto
            'inset-0 sm:inset-auto sm:h-[min(640px,calc(100dvh-8rem))] sm:w-[400px] sm:rounded-2xl sm:border sm:border-border',
            pos ? 'sm:top-[var(--tpy)] sm:left-[var(--tpx)]' : 'sm:right-5 sm:bottom-24',
            dragging && 'sm:select-none',
          )}
          style={pos ? ({ '--tpx': `${pos.x}px`, '--tpy': `${pos.y}px` } as CSSProperties) : undefined}
        >
          <header
            onPointerDown={startDrag}
            onDoubleClick={(e) => { if (!(e.target as HTMLElement).closest('button')) resetPos(); }}
            title="Arraste para mover · duplo clique para voltar ao canto"
            className={cn('flex items-center gap-3 bg-primary px-4 py-3 text-on-primary sm:cursor-grab sm:touch-none', dragging && 'sm:cursor-grabbing')}
          >
            <span className="relative shrink-0">
              <ThalitaAvatar className="size-10 ring-2 ring-on-primary/40 rounded-full" />
              <span title={statusText} aria-label={statusText} className={cn('absolute right-0 bottom-0 size-3 rounded-full ring-2 ring-primary', dot)} />
            </span>
            <div className="min-w-0 flex-1 leading-tight">
              <div className="font-semibold">Thalita</div>
              <div className="truncate text-xs opacity-80">
                {disabled
                  ? (customer ? 'Fora do ar no momento' : 'Desligada neste servidor (falta a chave da IA)')
                  : <><span className="font-semibold text-green-300">● Online</span> · {customer ? 'atendente virtual da Orden' : 'assistente virtual da Orden'}</>}
              </div>
            </div>
            {messages.length > 0 && (
              <button type="button" onClick={reset} aria-label="Nova conversa" title="Nova conversa"
                className="rounded-lg p-2 hover:bg-on-primary/15 focus-visible:ring-2 focus-visible:ring-on-primary/60 focus-visible:outline-none">
                <Icon name="refresh" className="size-5" />
              </button>
            )}
            <button type="button" onClick={() => setOpen(false)} aria-label="Fechar conversa"
              className="rounded-lg p-2 hover:bg-on-primary/15 focus-visible:ring-2 focus-visible:ring-on-primary/60 focus-visible:outline-none">
              <Icon name="close" className="size-5" />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto bg-bg px-4 py-4" aria-live="polite">
            {disabled && (
              <div role="alert" className="rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-sm text-warning">
                {customer ? (
                  <>A Thalita está fora do ar agora. Você ainda pode procurar na vitrine e, se não achar a peça, usar o botão
                    {' '}<strong>"Não achou? Peça à loja"</strong>.</>
                ) : (
                  <>A Thalita está desligada neste servidor. Quem administra o sistema liga configurando um provedor de IA
                    (<code>ASSISTANT_PROVIDER</code> e <code>ASSISTANT_API_KEY</code>); há opções grátis. Veja docs/10-assistente-ia.md.</>
                )}
              </div>
            )}

            {messages.length === 0 && role && (
              <div className="flex flex-col items-center px-2 py-4 text-center">
                <ThalitaAvatar className="size-24 rounded-full shadow-md ring-4 ring-surface" />
                <h2 className="mt-3 text-base font-semibold">Olá! Eu sou a Thalita</h2>
                <p className="text-sm text-muted">{customer ? 'Sua atendente virtual da Orden.' : 'Sua assistente virtual da Orden.'}</p>
                <ul className="mt-3 flex flex-wrap justify-center gap-x-3 gap-y-1 text-xs text-muted">
                  {(customer
                    ? ['Atendimento rápido e humanizado', 'Tire suas dúvidas', 'Acompanhe seu pedido']
                    : ['Números reais da loja', 'Tire suas dúvidas', 'Respeito ao seu perfil']
                  ).map((t) => (
                    <li key={t} className="flex items-center gap-1"><span aria-hidden className="text-primary">♥</span>{t}</li>
                  ))}
                </ul>
                <p className="mt-4 max-w-[18rem] text-sm text-muted">
                  Digite <strong>oi</strong> para me conhecer, ou escolha uma pergunta:
                </p>
                <div className="mt-4 flex flex-wrap justify-center gap-2">
                  {SUGGESTIONS[role].map((s) => (
                    <button key={s} type="button" disabled={disabled || busy} onClick={() => void send(s)}
                      className="rounded-full border border-border bg-surface px-3 py-1.5 text-left text-sm hover:bg-surface-2 disabled:opacity-50">
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m, i) => (
              <div key={i} className={cn('flex items-end gap-2', m.from === 'user' ? 'justify-end' : 'justify-start')}>
                {m.from !== 'user' && <ThalitaAvatar className="mb-0.5 size-7 shrink-0 rounded-full" />}
                <div
                  className={cn(
                    'max-w-[85%] rounded-2xl px-3.5 py-2.5',
                    m.from === 'user' && 'rounded-br-md bg-primary text-on-primary',
                    m.from === 'assistant' && 'rounded-bl-md border border-border bg-surface',
                    m.from === 'error' && 'rounded-bl-md bg-error-soft text-sm text-error',
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
              <div className="flex items-end gap-2">
                <ThalitaAvatar className="mb-0.5 size-7 shrink-0 rounded-full" />
                <div className="flex items-center gap-1.5 rounded-2xl rounded-bl-md border border-border bg-surface px-4 py-3" aria-label="Thalita está consultando os dados">
                  <span className="talita-dot size-1.5 rounded-full bg-muted" />
                  <span className="talita-dot size-1.5 rounded-full bg-muted" />
                  <span className="talita-dot size-1.5 rounded-full bg-muted" />
                </div>
              </div>
            )}
            <div ref={endRef} />
          </div>

          <form onSubmit={submit} className="border-t border-border bg-surface p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
            <div className="flex items-end gap-2">
              <label htmlFor="talita-input" className="sr-only">Mensagem para a Thalita</label>
              <textarea
                id="talita-input"
                ref={inputRef}
                rows={1}
                value={input}
                maxLength={2000}
                disabled={disabled}
                placeholder="Escreva para a Thalita…"
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    void send(input);
                  }
                }}
                className="max-h-[104px] min-h-10 flex-1 resize-none rounded-xl border border-border bg-bg px-3 py-2 text-base focus:border-primary focus:ring-2 focus:ring-primary/25 focus:outline-none disabled:opacity-60 sm:text-sm"
              />
              <button type="submit" disabled={disabled || busy || !input.trim()} aria-label="Enviar"
                className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary text-on-primary hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50">
                <Icon name="send" className="size-5" />
              </button>
            </div>
            <p className="mt-2 text-center text-[11px] text-muted">
              {customer
                ? 'Eu consulto a vitrine e os seus pedidos e, se você pedir, faço o pedido de uma peça para a loja.'
                : 'Eu só consulto, não altero nada. Confira números importantes nas telas.'}
              {!customer && status.data?.enabled && status.data.model ? ` · ${status.data.model}` : ''}
            </p>
          </form>
        </section>
      )}

      {/* Botão flutuante. No celular, com a conversa aberta ele some (a tela cheia tem o próprio fechar). */}
      <div className={cn('fixed right-4 bottom-4 z-30 flex items-end gap-3 sm:right-5 sm:bottom-5', open && 'max-sm:hidden')}>
        {hint && !open && (
          <button type="button" onClick={() => { dismissHint(); setOpen(true); }}
            className="talita-pop mb-2 max-w-[13rem] rounded-2xl rounded-br-sm border border-border bg-surface px-3.5 py-2.5 text-left text-sm shadow-lg">
            <span className="font-semibold">Oi! Eu sou a Thalita.</span>
            <span className="block text-muted">Precisa de ajuda com a loja?</span>
          </button>
        )}
        <button
          type="button"
          onClick={() => { dismissHint(); setOpen((v) => !v); }}
          aria-label={open ? 'Fechar conversa com a Thalita' : 'Abrir conversa com a Thalita'}
          aria-expanded={open}
          className="group relative grid size-16 place-items-center rounded-full bg-primary shadow-xl ring-4 ring-surface transition-transform hover:scale-105 focus-visible:ring-primary/60 focus-visible:outline-none motion-reduce:transition-none"
        >
          {open ? <Icon name="close" className="size-7 text-on-primary" /> : <ThalitaAvatar className="size-14 rounded-full" />}
          {!open && <span title={statusText} aria-label={statusText} className={cn('absolute right-0.5 bottom-0.5 size-4 rounded-full ring-2 ring-surface', dot)} />}
        </button>
      </div>
    </>
  );
}
