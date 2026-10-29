import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { purchaseStatusLabel, purchaseStatusTone } from '@/data/adapters';
import type { ProductDto, PurchaseOrderDto, PurchaseOrderStatus } from '@/data/dto';
import { usePlanning, usePurchaseOrderActions, usePurchaseOrders } from '@/data/hooks';
import { can } from '@/domain/permissions';
import { cn } from '@/lib/cn';
import { formatDate, formatDateTime, toIsoDate } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Textarea } from '../components/atoms/Field';
import { Icon } from '../components/atoms/Icon';
import { Modal } from '../components/molecules/Modal';
import { Pagination } from '../components/molecules/Pagination';
import { ProductPicker } from '../components/molecules/ProductPicker';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

type Tab = { id: string; label: string; status?: PurchaseOrderStatus; late?: boolean };

// Abas por etapa (V11): pedido feito → em trânsito → em conferência → entrada feita.
const TABS: Tab[] = [
  { id: 'open', label: 'Pedidos feitos', status: 'OPEN' },
  { id: 'transit', label: 'Em trânsito', status: 'IN_TRANSIT' },
  { id: 'checking', label: 'Em conferência', status: 'CHECKING' },
  { id: 'late', label: 'Atrasados', late: true },
  { id: 'received', label: 'Entradas', status: 'RECEIVED' },
  { id: 'cancelled', label: 'Cancelados', status: 'CANCELLED' },
  { id: 'all', label: 'Todos' },
];

const plusDays = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return toIsoDate(d);
};

// "12:30,14:10" (vindo da Reposição) → [{productId: 12, quantity: 30}, ...]
function parsePrefill(v: string | null) {
  if (!v) return [];
  return v.split(',').map((p) => p.split(':').map(Number)).filter(([id, q]) => id && id > 0 && q && q > 0)
    .map(([productId = 0, quantity = 1]) => ({ productId, quantity }));
}

// Spec crm/11: pedidos de compra ao fornecedor.
export function PurchaseOrdersPage() {
  const { role } = useSession();
  const [params, setParams] = useSearchParams();
  // Pela id, não pela posição: ao reordenar as abas (etapas, V11) o link "?atrasados=1" passou a abrir "Em trânsito".
  const [tab, setTab] = useState<Tab>(() => (params.get('atrasados') ? TABS.find((t) => t.id === 'late')! : TABS[0]!));
  const [page, setPage] = useState(0);
  const query = usePurchaseOrders(tab.status, !!tab.late, page);
  const prefill = parsePrefill(params.get('novo'));
  const [creating, setCreating] = useState(prefill.length > 0);
  const [acting, setActing] = useState<{ o: PurchaseOrderDto; kind: 'ship' | 'receive' | 'return' | 'reschedule' | 'cancel' } | null>(null);
  const { arrive } = usePurchaseOrderActions();
  const toast = useToast();
  const buyer = role ? can.buy(role) : false;
  const receiver = role ? can.receivePurchase(role) : false;

  function closeCreate() {
    setCreating(false);
    if (params.has('novo')) setParams({}, { replace: true });
  }

  return (
    <>
      <PageHeader
        title="Pedidos de compra"
        help={'O que foi pedido ao fornecedor e em que etapa está: pedido feito → em trânsito (saiu do fornecedor) → em conferência '
          + '(chegou na loja) → entrada feita (com a nota fiscal). Quem compra registra, marca o envio, muda a data ou cancela, sempre '
          + 'com o motivo. Quem recebe marca a chegada e, depois de conferir, dá entrada: todas as peças entram no estoque e os chamados '
          + 'delas fecham sozinhos. Pedido feito ou em trânsito com a data vencida aparece como atrasado.'}
        subtitle={buyer ? 'Registre o que pediu e acompanhe a chegada.' : 'Quando a mercadoria chegar, clique em Receber para dar entrada no estoque.'}
        actions={buyer && <Button onClick={() => setCreating(true)}><Icon name="plus" className="size-4" />Novo pedido</Button>}
      />
      <Card>
        <div className="flex flex-wrap gap-1 border-b border-border p-2" role="group" aria-label="Situação">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              aria-pressed={tab.id === t.id}
              onClick={() => { setTab(t); setPage(0); }}
              className={cn('rounded-md px-3 py-1.5 text-sm font-medium', tab.id === t.id ? 'bg-primary text-on-primary' : 'text-muted hover:bg-surface-2')}
            >
              {t.label}
            </button>
          ))}
        </div>
        <QueryState
          query={query}
          isEmpty={(d) => d.content.length === 0}
          empty={tab.id === 'late' ? 'Nenhum pedido atrasado.' : tab.id === 'checking' ? 'Nada em conferência agora.' : 'Nenhum pedido aqui.'}
        >
          {(d) => (
            <>
              <DataTable<PurchaseOrderDto>
                rows={d.content}
                rowKey={(o) => o.id}
                columns={[
                  {
                    header: 'Pedido',
                    cell: (o) => (
                      <span>
                        <span className="font-medium">nº {o.id} · {o.supplier}</span>
                        <span className="block text-xs text-muted">por {o.createdByName ?? '—'} em {formatDateTime(o.createdAt)}</span>
                        {o.note && <span className="block text-xs text-muted">{o.note}</span>}
                      </span>
                    ),
                  },
                  {
                    header: 'Peças',
                    cell: (o) => (
                      <ul className="text-sm">
                        {o.items.slice(0, 3).map((i) => (
                          <li key={i.productId}>{i.quantity}× {i.productName} <span className="text-xs text-muted">({i.size} · {i.color})</span></li>
                        ))}
                        {o.items.length > 3 && <li className="text-xs text-muted">+ {o.items.length - 3} peça(s) · {o.totalUnits} un. no total</li>}
                      </ul>
                    ),
                  },
                  {
                    header: 'Chega em',
                    cell: (o) => (
                      <span>
                        {formatDate(o.expectedDate)}
                        {o.late && <span className="block"><Badge tone="error">Atrasado</Badge></span>}
                        {o.rescheduleCount > 0 && <span className="block text-xs text-muted">data mudou {o.rescheduleCount}×</span>}
                        {o.shippedAt && <span className="block text-xs text-muted">saiu em {formatDateTime(o.shippedAt)}</span>}
                        {o.arrivedAt && <span className="block text-xs text-muted">chegou em {formatDateTime(o.arrivedAt)}</span>}
                      </span>
                    ),
                  },
                  {
                    header: 'Situação',
                    cell: (o) => (
                      <span>
                        <Badge tone={purchaseStatusTone[o.status]}>{purchaseStatusLabel[o.status]}</Badge>
                        {o.invoiceNumber && <span className="mt-0.5 block text-xs font-medium">NF {o.invoiceNumber}</span>}
                        {o.bonusInvoiceNumber && <span className="block text-xs font-medium">Bonificação NF {o.bonusInvoiceNumber}</span>}
                        {o.returnCount > 0 && <span className="block text-xs text-warning">nota devolvida {o.returnCount}×</span>}
                        {o.closedAt && <span className="mt-0.5 block text-xs text-muted">{o.closedByName ?? '—'} · {formatDateTime(o.closedAt)}</span>}
                        {o.closingNote && <span className="block text-xs text-muted">{o.closingNote}</span>}
                      </span>
                    ),
                  },
                  {
                    header: 'Ações',
                    align: 'right',
                    // Cada etapa mostra só o próximo passo de quem pode dá-lo (o back-end confere de novo).
                    cell: (o) => o.status === 'RECEIVED' || o.status === 'CANCELLED' ? null : (
                      <span className="flex flex-wrap justify-end gap-1">
                        {o.status === 'OPEN' && buyer && <Button size="sm" onClick={() => setActing({ o, kind: 'ship' })}>Saiu do fornecedor</Button>}
                        {o.status !== 'CHECKING' && receiver && (
                          <Button size="sm" variant={o.status === 'IN_TRANSIT' ? 'primary' : 'secondary'} loading={arrive.isPending && arrive.variables === o.id}
                            onClick={() => arrive.mutate(o.id, {
                              onSuccess: () => toast.success(`Pedido nº ${o.id} em conferência. Confira as peças e dê entrada com a nota fiscal.`),
                              onError: (err) => toast.error(err),
                            })}>
                            Chegou na loja
                          </Button>
                        )}
                        {o.status === 'CHECKING' && receiver && <Button size="sm" onClick={() => setActing({ o, kind: 'receive' })}>Dar entrada</Button>}
                        {o.status === 'CHECKING' && receiver && <Button size="sm" variant="secondary" onClick={() => setActing({ o, kind: 'return' })}>Devolver a nota</Button>}
                        {o.status !== 'CHECKING' && buyer && <Button size="sm" variant="secondary" onClick={() => setActing({ o, kind: 'reschedule' })}>Mudar data</Button>}
                        {buyer && <Button size="sm" variant="ghost" onClick={() => setActing({ o, kind: 'cancel' })}>Cancelar</Button>}
                      </span>
                    ),
                  },
                ]}
              />
              <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} onChange={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      {creating && <NewOrderModal prefill={prefill} onClose={closeCreate} />}
      {acting?.kind === 'ship' && <ShipModal o={acting.o} onClose={() => setActing(null)} />}
      {acting?.kind === 'receive' && <ReceiveModal o={acting.o} onClose={() => setActing(null)} />}
      {acting?.kind === 'return' && <ReturnModal o={acting.o} onClose={() => setActing(null)} />}
      {acting?.kind === 'reschedule' && <RescheduleModal o={acting.o} onClose={() => setActing(null)} />}
      {acting?.kind === 'cancel' && <CancelModal o={acting.o} onClose={() => setActing(null)} />}
    </>
  );
}

interface Line { productId: number; name: string; detail: string; quantity: string }

function NewOrderModal({ prefill, onClose }: { prefill: { productId: number; quantity: number }[]; onClose: () => void }) {
  const { create } = usePurchaseOrderActions();
  const toast = useToast();
  // A Reposição já tem nome, estoque e sugestão de cada peça: usa para mostrar as linhas que vieram prontas.
  const plan = usePlanning(30);
  const info = (id: number) => plan.data?.items.find((i) => i.productId === id);
  const [form, setForm] = useState({ supplier: '', expectedDate: plusDays(7), note: '' });
  const [lines, setLines] = useState<Line[]>(() => prefill.map((p) => ({ productId: p.productId, name: '', detail: '', quantity: String(p.quantity) })));
  const [error, setError] = useState<string>();

  function add(p: ProductDto) {
    if (lines.some((l) => l.productId === p.id)) return setError('Esta peça já está no pedido: mude a quantidade dela.');
    setError(undefined);
    const s = info(p.id);
    setLines((ls) => [...ls, {
      productId: p.id, name: p.name, detail: `${p.code} · tam. ${p.size} · ${p.color}`,
      quantity: String(s && s.suggestedQty > 0 ? s.suggestedQty : 1),
    }]);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (form.supplier.trim().length < 2) return setError('Informe o fornecedor.');
    if (!form.expectedDate) return setError('Informe quando o pedido deve chegar.');
    if (lines.length === 0) return setError('Inclua ao menos uma peça.');
    const items = lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity) }));
    if (items.some((i) => !Number.isInteger(i.quantity) || i.quantity < 1 || i.quantity > 9999)) {
      return setError('A quantidade de cada peça deve ser de 1 a 9999.');
    }
    setError(undefined);
    try {
      const o = await create.mutateAsync({ supplier: form.supplier.trim(), expectedDate: form.expectedDate, note: form.note.trim() || undefined, items });
      toast.success(`Pedido nº ${o.id} registrado. A Reposição já conta com ele.`);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal
      open
      size="lg"
      title="Novo pedido de compra"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button type="submit" form="purchase-form" loading={create.isPending}>Registrar pedido</Button>
        </>
      }
    >
      <form id="purchase-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p className="text-sm text-muted">Registre o que você pediu ao fornecedor. Isto não envia nada a ele: serve para a loja saber o que está a caminho.</p>
        <div className="grid gap-2 sm:grid-cols-2">
          <Input label="Fornecedor" value={form.supplier} placeholder="ex.: Malharia Vale do Sul" onChange={(e) => setForm({ ...form, supplier: e.target.value })} />
          <Input label="Chega em" type="date" min={plusDays(0)} max={plusDays(180)} value={form.expectedDate}
            onChange={(e) => setForm({ ...form, expectedDate: e.target.value })} />
        </div>
        <Input label="Observação (opcional)" value={form.note} placeholder="ex.: nº do pedido no fornecedor" onChange={(e) => setForm({ ...form, note: e.target.value })} />

        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium">Peças ({lines.length})</span>
          {lines.length === 0 && <p className="text-sm text-muted">Nenhuma peça ainda. Busque abaixo para incluir.</p>}
          {lines.map((l, idx) => {
            const s = info(l.productId);
            return (
              <div key={l.productId} className="flex items-end gap-2 rounded-lg bg-surface-2 px-3 py-2">
                <div className="min-w-0 flex-1 text-sm">
                  <span className="font-medium">{l.name || s?.name || `Produto ${l.productId}`}</span>
                  <span className="block text-xs text-muted">
                    {l.detail || (s ? `${s.code} · tam. ${s.size} · ${s.color}` : '')}
                    {s && ` · estoque ${s.stock} · sugestão ${s.suggestedQty}`}
                  </span>
                </div>
                <Input label="Quantidade" type="number" min={1} max={9999} className="w-28" value={l.quantity}
                  onChange={(e) => setLines((ls) => ls.map((x, i) => (i === idx ? { ...x, quantity: e.target.value } : x)))} />
                <Button type="button" size="sm" variant="ghost" aria-label={`Tirar ${l.name || s?.name || 'peça'}`}
                  onClick={() => setLines((ls) => ls.filter((_, i) => i !== idx))}>
                  <Icon name="trash" className="size-4" />
                </Button>
              </div>
            );
          })}
          <ProductPicker label="Incluir peça" onPick={add} placeholder="Digite nome ou código" />
        </div>
        {error && <p className="text-sm text-error">{error}</p>}
      </form>
    </Modal>
  );
}

function ShipModal({ o, onClose }: { o: PurchaseOrderDto; onClose: () => void }) {
  const { ship } = usePurchaseOrderActions();
  const toast = useToast();
  const [nf, setNf] = useState('');

  async function submit() {
    try {
      await ship.mutateAsync({ id: o.id, invoiceNumber: nf.trim() || undefined });
      toast.success(`Pedido nº ${o.id} em trânsito.`);
      onClose();
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <Modal
      open
      title={`Pedido nº ${o.id} saiu do fornecedor`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button onClick={() => void submit()} loading={ship.isPending}>Marcar em trânsito</Button>
        </>
      }
    >
      <p className="mb-3 text-sm">O fornecedor <strong>{o.supplier}</strong> despachou a mercadoria. Se a nota fiscal já veio, anote o número: ele vem pronto na hora da entrada.</p>
      <Input label="Nº da nota fiscal (opcional)" value={nf} maxLength={30} placeholder="ex.: 000.184.552" onChange={(e) => setNf(e.target.value)} />
    </Modal>
  );
}

// Conferência (regra de 29/10/2026): conta peça por peça. Chegou a menos → a nota volta (ReturnModal); chegou a mais → o
// extra entra com a nota de bonificação. Nunca se dá entrada "mais ou menos" para ajustar depois.
function ReceiveModal({ o, onClose }: { o: PurchaseOrderDto; onClose: () => void }) {
  const { receive, returnDelivery } = usePurchaseOrderActions();
  const toast = useToast();
  const [note, setNote] = useState('');
  const [nf, setNf] = useState(o.invoiceNumber ?? '');
  const [bonusNf, setBonusNf] = useState('');
  const [counts, setCounts] = useState<Record<number, string>>(() => Object.fromEntries(o.items.map((i) => [i.productId, String(i.quantity)])));
  const [returning, setReturning] = useState(false);
  const [returnNote, setReturnNote] = useState('');
  const [error, setError] = useState<string>();

  const items = o.items.map((i) => ({ productId: i.productId, receivedQuantity: Number(counts[i.productId] ?? i.quantity) }));
  const invalid = items.some((i) => !Number.isInteger(i.receivedQuantity) || i.receivedQuantity < 0);
  const short = o.items.filter((i) => Number(counts[i.productId]) < i.quantity);
  const bonusUnits = o.items.reduce((n, i) => n + Math.max(0, Number(counts[i.productId]) - i.quantity), 0);

  async function submit() {
    if (invalid) return setError('Confira as quantidades: só números inteiros, sem negativo.');
    if (short.length > 0) return setError('Chegou a menos: devolva a nota ao fornecedor (botão abaixo). Nada entra até ele corrigir.');
    if (!nf.trim()) return setError('Informe o número da nota fiscal.');
    if (bonusUnits > 0 && !bonusNf.trim()) return setError('Chegou a mais: informe o número da nota de bonificação.');
    try {
      const r = await receive.mutateAsync({ id: o.id, invoiceNumber: nf.trim(), bonusInvoiceNumber: bonusUnits > 0 ? bonusNf.trim() : undefined,
        note: note.trim() || undefined, items });
      const extra = r.bonusUnits > 0 ? ` ${r.bonusUnits} un. a mais entraram pela nota de bonificação.` : '';
      toast.success((r.requestsResolved > 0
        ? `Entrada feita. ${r.requestsResolved} chamado(s) de peça em falta fechado(s); quem abriu foi avisado.`
        : 'Entrada feita no estoque.') + extra);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  async function doReturn() {
    if (returnNote.trim().length < 3) return setError('Conte por que a nota voltou.');
    try {
      await returnDelivery.mutateAsync({ id: o.id, note: returnNote.trim(), items });
      toast.success('Nota devolvida ao fornecedor. Nada entrou no estoque; o pedido voltou para "Pedidos feitos".');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  if (returning) {
    return (
      <Modal open title={`Devolver a nota do pedido nº ${o.id}`} onClose={onClose}
        footer={<><Button variant="secondary" onClick={() => setReturning(false)}>Voltar à conferência</Button>
          <Button variant="danger" onClick={() => void doReturn()} loading={returnDelivery.isPending}>Devolver a nota</Button></>}>
        <p className="mb-3 text-sm">A mercadoria volta para <strong>{o.supplier}</strong> com a nota. Nada entra no estoque; o pedido volta a "Pedidos feitos" até o fornecedor corrigir e enviar de novo.</p>
        {short.length > 0 && (
          <ul className="mb-3 rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
            {short.map((i) => <li key={i.productId}>{i.productName}: chegaram {counts[i.productId]} de {i.quantity}</li>)}
          </ul>
        )}
        <Textarea label="Motivo" value={returnNote} placeholder="ex.: Vieram 8 de 10; peça errada; avaria" onChange={(e) => setReturnNote(e.target.value)} error={error} />
      </Modal>
    );
  }

  return (
    <Modal
      open
      title={`Dar entrada no pedido nº ${o.id}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button variant="secondary" onClick={() => { setError(undefined); setReturning(true); }}>Devolver a nota</Button>
          <Button onClick={() => void submit()} loading={receive.isPending} disabled={short.length > 0}
            title={short.length > 0 ? 'Chegou a menos: devolva a nota em vez de dar entrada.' : undefined}>
            Dar entrada no estoque
          </Button>
        </>
      }
    >
      <p className="mb-2 text-sm">Conte o que chegou de <strong>{o.supplier}</strong>, peça por peça. Ao dar entrada, cada peça entra no estoque e os chamados dela fecham sozinhos.</p>
      <ul className="mb-3 flex flex-col gap-1 text-sm">
        {o.items.map((i) => {
          const got = Number(counts[i.productId]);
          const diff = got - i.quantity;
          return (
            <li key={i.productId} className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-surface-2 px-3 py-1.5">
              <span className="min-w-0 flex-1">
                {i.productName} <span className="text-xs text-muted">{i.productCode} · {i.size} · {i.color}</span>
                <span className="block text-xs text-muted">pedido {i.quantity} · hoje {i.stockNow}</span>
              </span>
              <span className="flex items-center gap-2">
                <Input label="Chegou" type="number" min={0} className="w-20" value={counts[i.productId] ?? ''}
                  onChange={(e) => { setCounts({ ...counts, [i.productId]: e.target.value }); setError(undefined); }} />
                {diff < 0 && <Badge tone="error">faltam {-diff}</Badge>}
                {diff > 0 && <Badge tone="info">+{diff} a mais</Badge>}
                {diff === 0 && <Badge tone="success">bateu</Badge>}
              </span>
            </li>
          );
        })}
      </ul>
      {short.length > 0 && (
        <p className="mb-3 rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-sm text-warning">
          Chegou a menos. A regra é devolver a nota ao fornecedor: nada entra até ele corrigir.
        </p>
      )}
      <div className="flex flex-col gap-3">
        <Input label="Nº da nota fiscal" value={nf} maxLength={30} placeholder="ex.: 000.184.552"
          hint={o.invoiceNumber ? 'Veio do envio; corrija se a nota for outra.' : 'Obrigatório: é a nota que deu entrada.'}
          onChange={(e) => { setNf(e.target.value); setError(undefined); }} />
        {bonusUnits > 0 && (
          <Input label={`Nº da nota de bonificação (${bonusUnits} un. a mais)`} value={bonusNf} maxLength={30} placeholder="ex.: 000.031.907"
            hint="O que veio a mais do que o pedido entra por esta nota." onChange={(e) => { setBonusNf(e.target.value); setError(undefined); }} />
        )}
        <Textarea label="Observação (opcional)" value={note} placeholder="ex.: Conferido, tudo certo" onChange={(e) => setNote(e.target.value)} />
        {error && <p className="text-sm text-error">{error}</p>}
      </div>
    </Modal>
  );
}

// "Devolver a nota" direto da lista: abre a conferência já na tela de devolução.
function ReturnModal({ o, onClose }: { o: PurchaseOrderDto; onClose: () => void }) {
  const { returnDelivery } = usePurchaseOrderActions();
  const toast = useToast();
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();

  async function submit() {
    if (note.trim().length < 3) return setError('Conte por que a nota voltou.');
    try {
      await returnDelivery.mutateAsync({ id: o.id, note: note.trim() });
      toast.success('Nota devolvida ao fornecedor. Nada entrou no estoque; o pedido voltou para "Pedidos feitos".');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal open title={`Devolver a nota do pedido nº ${o.id}`} onClose={onClose}
      footer={<><Button variant="secondary" onClick={onClose}>Voltar</Button>
        <Button variant="danger" onClick={() => void submit()} loading={returnDelivery.isPending}>Devolver a nota</Button></>}>
      <p className="mb-3 text-sm">A mercadoria volta para <strong>{o.supplier}</strong> com a nota. Nada entra no estoque; o pedido volta a "Pedidos feitos" até o fornecedor corrigir e enviar de novo. Para contar peça por peça antes, use "Dar entrada".</p>
      <Textarea label="Motivo" value={note} placeholder="ex.: Vieram 8 de 10; peça errada; avaria" onChange={(e) => setNote(e.target.value)} error={error} />
    </Modal>
  );
}

function RescheduleModal({ o, onClose }: { o: PurchaseOrderDto; onClose: () => void }) {
  const { reschedule } = usePurchaseOrderActions();
  const toast = useToast();
  const [date, setDate] = useState(o.expectedDate);
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();

  async function submit() {
    if (date === o.expectedDate) return setError('Escolha uma data diferente da atual.');
    if (note.trim().length < 3) return setError('Conte por que a data mudou.');
    try {
      await reschedule.mutateAsync({ id: o.id, expectedDate: date, note: note.trim() });
      toast.success('Data atualizada. A data antiga fica no histórico.');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal
      open
      title={`Mudar a data do pedido nº ${o.id}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button onClick={() => void submit()} loading={reschedule.isPending}>Salvar nova data</Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <p className="text-sm">Hoje está para <strong>{formatDate(o.expectedDate)}</strong>.</p>
        <Input label="Nova data" type="date" min={plusDays(0)} max={plusDays(180)} value={date} onChange={(e) => setDate(e.target.value)} />
        <Textarea label="Motivo" value={note} placeholder="ex.: Fornecedor atrasou a produção" onChange={(e) => setNote(e.target.value)} />
        {error && <p className="text-sm text-error">{error}</p>}
      </div>
    </Modal>
  );
}

function CancelModal({ o, onClose }: { o: PurchaseOrderDto; onClose: () => void }) {
  const { cancel } = usePurchaseOrderActions();
  const toast = useToast();
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();

  async function submit() {
    if (note.trim().length < 3) return setError('Informe o motivo.');
    try {
      await cancel.mutateAsync({ id: o.id, note: note.trim() });
      toast.success('Pedido cancelado. Ele continua na aba Cancelados.');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal
      open
      title={`Cancelar pedido nº ${o.id}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button variant="danger" onClick={() => void submit()} loading={cancel.isPending}>Cancelar pedido</Button>
        </>
      }
    >
      <p className="mb-3 text-sm">As peças deste pedido deixam de contar como "a caminho" na Reposição.</p>
      <Textarea label="Motivo do cancelamento" value={note} placeholder="ex.: Fornecedor sem a peça" onChange={(e) => setNote(e.target.value)} error={error} />
    </Modal>
  );
}
