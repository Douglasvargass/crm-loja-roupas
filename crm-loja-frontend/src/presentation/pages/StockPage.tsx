import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useToast } from '@/app/context/ToastContext';
import { movementLabel } from '@/data/adapters';
import { useProduct, useProducts, useStockAlerts, useStockMovements, useStockOperation } from '@/data/hooks';
import type { AvailabilityFilter, ProductDto, StockMovementDto, StockMovementType } from '@/data/dto';
import { formatDateTime } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Select } from '../components/atoms/Field';
import { Modal } from '../components/molecules/Modal';
import { Pagination } from '../components/molecules/Pagination';
import { ProductPicker } from '../components/molecules/ProductPicker';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, Filters, PageHeader } from '../components/templates/Page';
import { MovementQty } from './products/ProductDetailPage';
import { StockBadge } from './products/ProductsPage';

type Op = { kind: 'entry' | 'adjustment'; product?: ProductDto };

export function StockPage() {
  const [params, setParams] = useSearchParams();
  const preselectedId = Number(params.get('entrada') ?? 0);
  const [op, setOp] = useState<Op | null>(null);
  const openPreselected = useCallback(
    (p: ProductDto) => {
      setOp({ kind: 'entry', product: p });
      setParams({}, { replace: true });
    },
    [setParams],
  );

  return (
    <>
      <PageHeader
        title="Estoque"
        subtitle="Saldo, entradas, ajustes e histórico de movimentações."
        actions={
          <>
            <Button onClick={() => setOp({ kind: 'entry' })}>Registrar entrada</Button>
            <Button variant="secondary" onClick={() => setOp({ kind: 'adjustment' })}>Registrar ajuste</Button>
          </>
        }
      />
      <Alerts onEntry={(p) => setOp({ kind: 'entry', product: p })} />
      <Balances onOp={setOp} />
      <History />

      {preselectedId > 0 && !op && (
        <PreselectedEntry id={preselectedId} onReady={openPreselected} />
      )}
      {op && <StockOperationModal op={op} onClose={() => setOp(null)} />}
    </>
  );
}

// Atalho vindo do dashboard/produto (?entrada=id): carrega o produto e abre a entrada.
function PreselectedEntry({ id, onReady }: { id: number; onReady: (p: ProductDto) => void }) {
  const q = useProduct(id);
  const product = q.data?.product;
  useEffect(() => {
    if (product) onReady(product);
  }, [product, onReady]);
  return null;
}

function Alerts({ onEntry }: { onEntry: (p: ProductDto) => void }) {
  const query = useStockAlerts();
  return (
    <QueryState query={query}>
      {(a) =>
        a.lowStock.length + a.outOfStock.length === 0 ? null : (
          <Card title="Precisa repor" className="mb-4" actions={<span className="flex gap-2"><Badge tone="warning">{a.lowStock.length} baixo</Badge><Badge tone="error">{a.outOfStock.length} zerado</Badge></span>}>
            <ul className="divide-y divide-border">
              {[...a.outOfStock, ...a.lowStock].map((p) => (
                <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <StockBadge p={p} />
                    <Link to={`/produtos/${p.id}`} className="truncate hover:underline">{p.name}</Link>
                    <span className="text-xs text-muted">{p.code}</span>
                  </span>
                  <span className="flex items-center gap-3">
                    <span className="tabular text-muted">{p.stockQty} / mín. {p.minStock}</span>
                    <Button size="sm" variant="secondary" onClick={() => onEntry(p)}>Entrada</Button>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        )
      }
    </QueryState>
  );
}

function Balances({ onOp }: { onOp: (op: Op) => void }) {
  const [availability, setAvailability] = useState<AvailabilityFilter>('ALL');
  const [name, setName] = useState('');
  const [page, setPage] = useState(0);
  const query = useProducts({ name, availability, page });
  return (
    <Card title="Saldo por produto" className="mb-4">
      <Filters>
        <Input label="Produto" placeholder="Nome" value={name} onChange={(e) => { setName(e.target.value); setPage(0); }} />
        <Select label="Nível de estoque" value={availability} onChange={(e) => { setAvailability(e.target.value as AvailabilityFilter); setPage(0); }}>
          <option value="ALL">Todos</option>
          <option value="AVAILABLE">Disponíveis</option>
          <option value="LOW_STOCK">Estoque baixo</option>
          <option value="OUT_OF_STOCK">Sem estoque</option>
          <option value="INACTIVE">Inativos</option>
        </Select>
      </Filters>
      <QueryState query={query} isEmpty={(d) => d.content.length === 0} empty="Nenhum produto com esse filtro.">
        {(data) => (
          <>
            <DataTable<ProductDto>
              rows={data.content}
              rowKey={(p) => p.id}
              columns={[
                { header: 'Código', cell: (p) => <span className="font-mono text-xs">{p.code}</span> },
                { header: 'Produto', cell: (p) => <Link to={`/produtos/${p.id}`} className="font-medium hover:underline">{p.name}</Link> },
                { header: 'Saldo', cell: (p) => <span className="tabular font-semibold">{p.stockQty}</span>, align: 'right' },
                { header: 'Mínimo', cell: (p) => <span className="tabular text-muted">{p.minStock}</span>, align: 'right' },
                { header: 'Situação', cell: (p) => <StockBadge p={p} /> },
                {
                  header: 'Ações',
                  align: 'right',
                  cell: (p) => (
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="ghost" onClick={() => onOp({ kind: 'entry', product: p })}>Entrada</Button>
                      <Button size="sm" variant="ghost" onClick={() => onOp({ kind: 'adjustment', product: p })}>Ajuste</Button>
                    </div>
                  ),
                },
              ]}
            />
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
          </>
        )}
      </QueryState>
    </Card>
  );
}

function History() {
  const [type, setType] = useState<StockMovementType | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [product, setProduct] = useState<ProductDto | null>(null);
  const [page, setPage] = useState(0);
  const query = useStockMovements({ productId: product?.id, type: type || undefined, from: from || undefined, to: to || undefined, page });

  return (
    <Card title="Histórico de movimentações">
      <Filters>
        {product ? (
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium">Produto</span>
            <div className="flex h-10 items-center justify-between rounded-lg border border-border px-3 text-sm">
              <span className="truncate">{product.name}</span>
              <button type="button" className="text-xs text-primary" onClick={() => { setProduct(null); setPage(0); }}>Limpar</button>
            </div>
          </div>
        ) : (
          <ProductPicker label="Produto" onPick={(p) => { setProduct(p); setPage(0); }} />
        )}
        <Select label="Tipo" value={type} onChange={(e) => { setType(e.target.value as StockMovementType | ''); setPage(0); }}>
          <option value="">Todos</option>
          {(Object.keys(movementLabel) as StockMovementType[]).map((t) => <option key={t} value={t}>{movementLabel[t]}</option>)}
        </Select>
        <Input label="De" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <Input label="Até" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
      </Filters>
      <QueryState query={query} isEmpty={(d) => d.content.length === 0} empty="Nenhuma movimentação com esses filtros.">
        {(data) => (
          <>
            <DataTable<StockMovementDto>
              rows={data.content}
              rowKey={(m) => m.id}
              columns={[
                { header: 'Data/hora', cell: (m) => formatDateTime(m.createdAt) },
                { header: 'Produto', cell: (m) => <Link to={`/produtos/${m.productId}`} className="hover:underline">{m.productName}</Link> },
                { header: 'Tipo', cell: (m) => movementLabel[m.type] },
                { header: 'Qtd.', cell: (m) => <MovementQty m={m} />, align: 'right' },
                { header: 'Saldo após', cell: (m) => <span className="tabular">{m.balanceAfter}</span>, align: 'right' },
                {
                  header: 'Origem / observação',
                  cell: (m) => m.saleId
                    ? <Link to={`/vendas/${m.saleId}`} className="text-primary hover:underline">Venda #{m.saleId}</Link>
                    : <span className="text-muted">{m.reason ?? '—'}</span>,
                },
              ]}
            />
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
          </>
        )}
      </QueryState>
    </Card>
  );
}

function StockOperationModal({ op, onClose }: { op: Op; onClose: () => void }) {
  const [product, setProduct] = useState<ProductDto | undefined>(op.product);
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const mutation = useStockOperation();
  const toast = useToast();
  const entry = op.kind === 'entry';

  async function submit(e: FormEvent) {
    e.preventDefault();
    const q = Number(quantity);
    const errs: Record<string, string> = {};
    if (!product) errs.product = 'Escolha o produto.';
    if (!/^-?\d+$/.test(quantity.trim()) || q === 0) errs.quantity = entry ? 'Informe uma quantidade maior que zero.' : 'Informe a quantidade (ex.: 3 ou -2).';
    else if (entry && q < 0) errs.quantity = 'Entrada é sempre positiva. Para retirar, use Ajuste.';
    else if (product && product.stockQty + q < 0) errs.quantity = `Saldo atual ${product.stockQty}; ajuste de ${q} deixaria estoque negativo.`;
    if (!entry && !reason.trim()) errs.reason = 'Informe o motivo do ajuste.';
    setErrors(errs);
    if (Object.keys(errs).length || !product) return;
    try {
      const m = await mutation.mutateAsync({ kind: op.kind, productId: product.id, quantity: q, reason: reason.trim() });
      toast.success(`${entry ? 'Entrada' : 'Ajuste'} registrado. Saldo de ${product.name}: ${m.balanceAfter}.`);
      onClose();
    } catch (err) {
      setErrors({ quantity: err instanceof Error ? err.message : 'Erro' });
    }
  }

  return (
    <Modal
      open
      title={entry ? 'Registrar entrada' : 'Registrar ajuste'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="stock-form" loading={mutation.isPending}>Registrar</Button>
        </>
      }
    >
      <form id="stock-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        {product ? (
          <div className="rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium">{product.name}</span>
              {!op.product && <button type="button" className="text-xs text-primary" onClick={() => setProduct(undefined)}>Trocar</button>}
            </div>
            <span className="text-xs text-muted">{product.code} · saldo atual {product.stockQty}</span>
          </div>
        ) : (
          <>
            <ProductPicker label="Produto" onPick={setProduct} />
            {errors.product && <p className="text-xs text-error">{errors.product}</p>}
          </>
        )}
        <Input
          label={entry ? 'Quantidade recebida' : 'Quantidade (+ ou −)'}
          inputMode="numeric"
          placeholder={entry ? '10' : '-2'}
          value={quantity}
          error={errors.quantity}
          hint={entry ? undefined : 'Positivo soma, negativo retira (perda, avaria, inventário).'}
          onChange={(e) => setQuantity(e.target.value)}
        />
        <Input
          label={entry ? 'Observação (opcional)' : 'Motivo'}
          placeholder={entry ? 'Ex.: NF 1234 do fornecedor' : 'Ex.: peça com defeito'}
          value={reason}
          error={errors.reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </form>
    </Modal>
  );
}
