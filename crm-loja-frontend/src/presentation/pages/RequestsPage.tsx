import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { customerLabel, requestStatusLabel, requestStatusTone } from '@/data/adapters';
import type { CustomerDto, ProductDto, StockRequestDto, StockRequestStatus } from '@/data/dto';
import { useProduct, useRequestActions, useRequests } from '@/data/hooks';
import { can } from '@/domain/permissions';
import { cn } from '@/lib/cn';
import { formatDateTime } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Textarea } from '../components/atoms/Field';
import { Icon } from '../components/atoms/Icon';
import { CustomerPicker } from '../components/molecules/CustomerPicker';
import { Modal } from '../components/molecules/Modal';
import { Pagination } from '../components/molecules/Pagination';
import { ProductPicker } from '../components/molecules/ProductPicker';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

const TABS: { id: StockRequestStatus | undefined; label: string }[] = [
  { id: 'OPEN', label: 'Abertos' },
  { id: 'RESOLVED', label: 'Resolvidos' },
  { id: 'CANCELLED', label: 'Cancelados' },
  { id: undefined, label: 'Todos' },
];

// Spec crm/09: chamados de peça em falta.
export function RequestsPage() {
  const { role, me } = useSession();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<StockRequestStatus | undefined>('OPEN');
  const [page, setPage] = useState(0);
  const query = useRequests(status, page);
  const preProduct = Number(params.get('novo') ?? 0);
  const [creating, setCreating] = useState(preProduct > 0);
  const [closing, setClosing] = useState<{ r: StockRequestDto; to: 'resolve' | 'cancel' } | null>(null);
  const seesAll = role ? can.resolveRequest(role) : false;

  function closeCreate() {
    setCreating(false);
    if (params.has('novo')) setParams({}, { replace: true });
  }

  return (
    <>
      <PageHeader
        title="Chamados" help="Peça que o cliente queria e não tinha. Quem atende abre; estoque e compras resolvem. Quando a peça chega, o chamado fecha sozinho."
        subtitle={seesAll
          ? 'Peças que os clientes queriam e não tinha. Resolva ou cancele com uma nota.'
          : 'As peças que seus clientes queriam e não tinha. Você é avisado no sino quando resolver.'}
        actions={role && can.openRequest(role) && (
          <Button onClick={() => setCreating(true)}><Icon name="plus" className="size-4" />Novo chamado</Button>
        )}
      />
      <Card>
        <div className="flex flex-wrap gap-1 border-b border-border p-2" role="group" aria-label="Situação">
          {TABS.map((t) => (
            <button
              key={t.label}
              type="button"
              aria-pressed={status === t.id}
              onClick={() => { setStatus(t.id); setPage(0); }}
              className={cn('rounded-md px-3 py-1.5 text-sm font-medium', status === t.id ? 'bg-primary text-on-primary' : 'text-muted hover:bg-surface-2')}
            >
              {t.label}
            </button>
          ))}
        </div>
        <QueryState
          query={query}
          isEmpty={(d) => d.content.length === 0}
          empty={status === 'OPEN' ? 'Nenhum chamado aberto. Tudo em ordem!' : 'Nenhum chamado aqui.'}
        >
          {(d) => (
            <>
              <DataTable<StockRequestDto>
                rows={d.content}
                rowKey={(r) => r.id}
                columns={[
                  {
                    header: 'Peça',
                    cell: (r) => (
                      <span>
                        <span className="font-medium">{r.description}</span>
                        <span className="block text-xs text-muted">
                          {[r.size && `tam. ${r.size}`, r.color, `${r.quantity} un.`, r.productCode].filter(Boolean).join(' · ')}
                        </span>
                      </span>
                    ),
                  },
                  { header: 'Cliente', cell: (r) => <span className={r.customerName ? '' : 'text-muted'}>{r.customerId ? customerLabel(r.customerName) : '—'}</span> },
                  {
                    header: 'Aberto por',
                    cell: (r) => (
                      <span className="text-sm">
                        {r.openedByName ?? '—'}
                        {r.openedVia === 'ASSISTANT' && <span className="text-xs text-muted"> (pela Thalita)</span>}
                        <span className="block text-xs text-muted">{formatDateTime(r.createdAt)}</span>
                      </span>
                    ),
                  },
                  {
                    header: 'Situação',
                    cell: (r) => (
                      <span>
                        <Badge tone={requestStatusTone[r.status]}>{requestStatusLabel[r.status]}</Badge>
                        {r.closingNote && <span className="mt-0.5 block text-xs text-muted">{r.closingNote}</span>}
                      </span>
                    ),
                  },
                  {
                    header: 'Ações',
                    align: 'right',
                    cell: (r) => r.status !== 'OPEN' ? null : (
                      <span className="flex justify-end gap-1">
                        {seesAll && <Button size="sm" variant="secondary" onClick={() => setClosing({ r, to: 'resolve' })}>Resolver</Button>}
                        {(seesAll || r.openedById === me?.id) && (
                          <Button size="sm" variant="ghost" onClick={() => setClosing({ r, to: 'cancel' })}>Cancelar</Button>
                        )}
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
      {creating && <NewRequestModal productId={preProduct || undefined} onClose={closeCreate} />}
      {closing && <CloseModal r={closing.r} to={closing.to} onClose={() => setClosing(null)} />}
    </>
  );
}

function NewRequestModal({ productId, onClose }: { productId?: number; onClose: () => void }) {
  const { open } = useRequestActions();
  const toast = useToast();
  const pre = useProduct(productId ?? 0);
  const [product, setProduct] = useState<ProductDto | null>(null);
  const [customer, setCustomer] = useState<CustomerDto | null>(null);
  const [form, setForm] = useState({ description: '', size: '', color: '', quantity: '1' });
  const [error, setError] = useState<string>();

  // Veio da ficha do produto: preenche com ele assim que carregar (uma vez só).
  const chosen = product ?? pre.data?.product ?? null;
  const description = form.description || (chosen ? chosen.name : '');

  function pick(p: ProductDto) {
    setProduct(p);
    setForm((f) => ({ ...f, description: p.name, size: p.size, color: p.color }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const quantity = Number(form.quantity);
    if (description.trim().length < 3) return setError('Descreva a peça que o cliente queria.');
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) return setError('A quantidade deve ser de 1 a 99.');
    setError(undefined);
    try {
      await open.mutateAsync({
        productId: chosen?.id,
        description: description.trim(),
        size: (form.size || chosen?.size || '').trim() || undefined,
        color: (form.color || chosen?.color || '').trim() || undefined,
        quantity,
        customerId: customer?.id,
      });
      toast.success('Chamado aberto. O estoque e o gerente foram avisados no sino.');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal
      open
      title="Novo chamado de peça em falta"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="request-form" loading={open.isPending}>Abrir chamado</Button>
        </>
      }
    >
      <form id="request-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p className="text-sm text-muted">O cliente queria uma peça que acabou ou que a loja não tem? Registre aqui. Peça com estoque é para vender, não abre chamado.</p>
        {chosen ? (
          <p className="flex items-center justify-between gap-2 rounded-lg bg-surface-2 px-3 py-2 text-sm">
            <span>{chosen.name} <span className="text-xs text-muted">{chosen.code} · estoque {chosen.stockQty}</span></span>
            <button type="button" className="text-xs text-primary hover:underline" onClick={() => { setProduct(null); setForm((f) => ({ ...f, description: '' })); }}>
              trocar
            </button>
          </p>
        ) : (
          <ProductPicker label="Produto do catálogo (opcional)" onPick={pick} placeholder="Digite nome ou código, ou deixe em branco" />
        )}
        <Input label="O que o cliente queria" value={description} placeholder="ex.: Calça jeans skinny 44 azul"
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <div className="grid grid-cols-3 gap-2">
          <Input label="Tamanho" value={form.size || chosen?.size || ''} onChange={(e) => setForm({ ...form, size: e.target.value })} />
          <Input label="Cor" value={form.color || chosen?.color || ''} onChange={(e) => setForm({ ...form, color: e.target.value })} />
          <Input label="Quantidade" type="number" min={1} max={99} value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} />
        </div>
        {customer ? (
          <p className="flex items-center justify-between rounded-lg bg-surface-2 px-3 py-2 text-sm">
            Cliente: {customer.fullName}
            <button type="button" className="text-xs text-primary hover:underline" onClick={() => setCustomer(null)}>tirar</button>
          </p>
        ) : (
          <CustomerPicker label="Cliente (opcional)" onPick={setCustomer} />
        )}
        {error && <p className="text-sm text-error">{error}</p>}
      </form>
    </Modal>
  );
}

function CloseModal({ r, to, onClose }: { r: StockRequestDto; to: 'resolve' | 'cancel'; onClose: () => void }) {
  const actions = useRequestActions();
  const toast = useToast();
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();
  const m = to === 'resolve' ? actions.resolve : actions.cancel;
  const missing = Math.max(0, 3 - note.trim().length);

  async function submit() {
    if (missing > 0) return setError(to === 'resolve' ? 'Conte como foi resolvido.' : 'Informe o motivo.');
    try {
      await m.mutateAsync({ id: r.id, note: note.trim() });
      toast.success(to === 'resolve' ? 'Chamado resolvido. Quem abriu vai ver no sino.' : 'Chamado cancelado.');
      onClose();
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <Modal
      open
      title={to === 'resolve' ? 'Resolver chamado' : 'Cancelar chamado'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button onClick={() => void submit()} loading={m.isPending}>{to === 'resolve' ? 'Resolver' : 'Cancelar chamado'}</Button>
        </>
      }
    >
      <p className="mb-3 text-sm"><strong>{r.description}</strong> · {r.quantity} un.</p>
      <Textarea
        label={to === 'resolve' ? 'Como foi resolvido' : 'Motivo do cancelamento'}
        value={note}
        placeholder={to === 'resolve' ? 'ex.: Pedido ao fornecedor, chega sexta' : 'ex.: Cliente desistiu'}
        onChange={(e) => setNote(e.target.value)}
        error={error}
      />
    </Modal>
  );
}
