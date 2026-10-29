import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { customerLabel, saleStatusLabel, saleStatusTone } from '@/data/adapters';
import { useSale, useSaleActions } from '@/data/hooks';
import type { ProductDto, SaleDto, SaleItemDto } from '@/data/dto';
import { can } from '@/domain/permissions';
import { formatDateTime, formatMoney, maskCpf } from '@/lib/format';
import { Badge } from '../../components/atoms/Badge';
import { Button } from '../../components/atoms/Button';
import { Input } from '../../components/atoms/Field';
import { Icon } from '../../components/atoms/Icon';
import { ConfirmDialog } from '../../components/molecules/ConfirmDialog';
import { ProductPicker } from '../../components/molecules/ProductPicker';
import { QueryState } from '../../components/molecules/QueryState';
import { Card, Detail, PageHeader } from '../../components/templates/Page';

export function SaleDetailPage() {
  const query = useSale(Number(useParams().id));
  return <QueryState query={query}>{(sale) => <SaleView sale={sale} />}</QueryState>;
}

function SaleView({ sale }: { sale: SaleDto }) {
  const { role } = useSession();
  const toast = useToast();
  const navigate = useNavigate();
  const actions = useSaleActions(sale.id);
  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string>();
  const draft = sale.status === 'DRAFT';
  const overStock = sale.items.filter((i) => i.quantity > i.availableStock);

  async function confirm() {
    try {
      await actions.confirm.mutateAsync();
      toast.success(`Venda #${sale.id} confirmada. Estoque atualizado.`);
    } catch (err) {
      toast.error(err);
    } finally {
      setConfirming(false);
    }
  }

  async function cancel() {
    if (!reason.trim()) {
      setReasonError('Informe o motivo do cancelamento.');
      return;
    }
    try {
      await actions.cancel.mutateAsync(reason.trim());
      toast.success(sale.status === 'CONFIRMED' ? 'Venda cancelada. As peças voltaram para o estoque.' : 'Venda cancelada.');
      setCancelling(false);
    } catch (err) {
      setReasonError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <>
      <PageHeader
        title={`Venda #${sale.id}`} help="Aberta: dá para mudar os itens. Confirmar baixa o estoque de todos os itens, ou de nenhum se faltar algum. Venda nunca é apagada: cancela-se com motivo."
        subtitle={`${sale.channel === 'ONLINE' ? 'Pedido online · ' : ''}Aberta em ${formatDateTime(sale.createdAt)}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => navigate('/vendas')}>Voltar</Button>
            {sale.status !== 'CANCELLED' && role && can.cancelSale(role) && (
              <Button variant="secondary" onClick={() => setCancelling(true)}>Cancelar venda</Button>
            )}
            {draft && (
              <Button onClick={() => setConfirming(true)} disabled={sale.items.length === 0 || overStock.length > 0}>
                Confirmar venda
              </Button>
            )}
          </>
        }
      />
      {/* Nunca um botão travado sem explicar: diz o que falta para confirmar. */}
      {draft && (sale.items.length === 0 || overStock.length > 0) && (
        <p className="mb-4 rounded-lg border border-warning/30 bg-warning-soft px-3 py-2 text-sm text-warning">
          Para confirmar, ainda falta: {sale.items.length === 0
            ? 'adicionar ao menos um produto.'
            : `ajustar a quantidade de ${overStock.map((i) => i.productName).join(', ')} (acima do estoque).`}
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="flex flex-col gap-4 lg:col-span-2">
          {draft && <AddItem saleId={sale.id} items={sale.items} />}
          <Card title="Itens">
            {sale.items.length === 0 ? (
              <p className="p-6 text-center text-sm text-muted">Nenhum produto ainda. Busque acima para adicionar.</p>
            ) : (
              <ul className="divide-y divide-border">
                {sale.items.map((i) => <ItemRow key={i.id} saleId={sale.id} item={i} editable={draft} />)}
              </ul>
            )}
            <div className="flex items-center justify-between border-t border-border px-4 py-3">
              <span className="text-sm text-muted">Total</span>
              <span className="tabular text-xl font-semibold">{formatMoney(sale.total)}</span>
            </div>
          </Card>
        </div>

        <Card title="Resumo">
          <dl className="flex flex-col gap-3 p-4">
            <Detail label="Status"><Badge tone={saleStatusTone[sale.status]}>{saleStatusLabel[sale.status]}</Badge></Detail>
            <Detail label="Cliente">
              {sale.customerId ? (
                <Link to={`/clientes/${sale.customerId}`} className="text-primary hover:underline">{sale.customerName}</Link>
              ) : customerLabel(null)}
              {sale.customerCpf && <div className="tabular text-xs text-muted">{maskCpf(sale.customerCpf)}</div>}
            </Detail>
            <Detail label="Peças">{sale.items.reduce((s, i) => s + i.quantity, 0)}</Detail>
            {sale.confirmedAt && <Detail label="Confirmada em">{formatDateTime(sale.confirmedAt)}</Detail>}
            {sale.cancelledAt && (
              <>
                <Detail label="Cancelada em">{formatDateTime(sale.cancelledAt)}</Detail>
                <Detail label="Motivo">{sale.cancelReason}</Detail>
              </>
            )}
          </dl>
          {!draft && (
            <p className="border-t border-border px-4 py-3 text-xs text-muted">
              Preços e nomes são os do momento da venda, mesmo que o produto tenha mudado depois.
            </p>
          )}
        </Card>
      </div>

      <ConfirmDialog
        open={confirming}
        title="Confirmar venda"
        message={<>Confirmar a venda de <strong>{formatMoney(sale.total)}</strong>? O estoque de cada item será baixado.</>}
        confirmLabel="Confirmar"
        loading={actions.confirm.isPending}
        onConfirm={() => void confirm()}
        onClose={() => setConfirming(false)}
      />
      <ConfirmDialog
        open={cancelling}
        title={`Cancelar venda #${sale.id}`}
        message={sale.status === 'CONFIRMED'
          ? 'As quantidades voltam para o estoque. A venda continua no histórico como cancelada.'
          : 'A venda está aberta: nada muda no estoque.'}
        confirmLabel="Cancelar venda"
        danger
        loading={actions.cancel.isPending}
        onConfirm={() => void cancel()}
        onClose={() => setCancelling(false)}
      >
        <Input className="mt-3" label="Motivo" value={reason} error={reasonError} autoFocus
          onChange={(e) => { setReason(e.target.value); setReasonError(undefined); }} />
      </ConfirmDialog>
    </>
  );
}

function AddItem({ saleId, items }: { saleId: number; items: SaleItemDto[] }) {
  const { addItem } = useSaleActions(saleId);
  const toast = useToast();
  const [product, setProduct] = useState<ProductDto | null>(null);
  const [qty, setQty] = useState('1');
  const inCart = product ? items.find((i) => i.productId === product.id)?.quantity ?? 0 : 0;
  const n = Number(qty);
  const warn = product && n + inCart > product.stockQty
    ? `Tem ${product.stockQty} em estoque${inCart ? ` e ${inCart} já estão na venda` : ''}.`
    : undefined;

  async function add() {
    if (!product || !Number.isInteger(n) || n < 1) return;
    try {
      await addItem.mutateAsync({ productId: product.id, quantity: n });
      setProduct(null);
      setQty('1');
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <Card title="Adicionar produto">
      <div className="flex flex-col gap-3 p-4">
        {product ? (
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-0 flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm">
              <div className="flex justify-between gap-2">
                <span className="truncate font-medium">{product.name}</span>
                <button type="button" className="text-xs text-primary" onClick={() => setProduct(null)}>Trocar</button>
              </div>
              <span className="text-xs text-muted">{product.code} · {formatMoney(product.price)} · {product.stockQty} em estoque</span>
            </div>
            <Input className="w-24" label="Qtd." inputMode="numeric" value={qty} error={warn}
              onChange={(e) => setQty(e.target.value.replace(/\D/g, ''))} />
            <Button onClick={() => void add()} loading={addItem.isPending} disabled={!!warn || n < 1}>
              <Icon name="plus" className="size-4" />Adicionar
            </Button>
          </div>
        ) : (
          <ProductPicker label="Produto" onlyAvailable onPick={setProduct} />
        )}
      </div>
    </Card>
  );
}

function ItemRow({ saleId, item, editable }: { saleId: number; item: SaleItemDto; editable: boolean }) {
  const { updateItem, removeItem } = useSaleActions(saleId);
  const toast = useToast();
  const over = editable && item.quantity > item.availableStock;

  async function change(quantity: number) {
    if (quantity < 1) return;
    try {
      await updateItem.mutateAsync({ itemId: item.id, quantity });
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
      <div className="min-w-0 flex-1">
        <div className="truncate font-medium">{item.productName}</div>
        <div className="text-xs text-muted">{item.productCode} · {formatMoney(item.unitPrice)} cada</div>
        {over && <div className="text-xs text-error">Só há {item.availableStock} em estoque.</div>}
      </div>
      {editable ? (
        <div className="flex items-center gap-1">
          <Button size="sm" variant="secondary" aria-label="Diminuir" disabled={item.quantity <= 1 || updateItem.isPending}
            onClick={() => void change(item.quantity - 1)}>−</Button>
          <span className="tabular w-8 text-center">{item.quantity}</span>
          <Button size="sm" variant="secondary" aria-label="Aumentar"
            title={item.quantity >= item.availableStock ? `Só há ${item.availableStock} em estoque` : undefined} disabled={item.quantity >= item.availableStock || updateItem.isPending}
            onClick={() => void change(item.quantity + 1)}>+</Button>
        </div>
      ) : (
        <span className="tabular text-muted">{item.quantity} un.</span>
      )}
      <span className="tabular w-24 text-right font-medium">{formatMoney(item.subtotal)}</span>
      {editable && (
        <Button size="sm" variant="ghost" aria-label={`Remover ${item.productName}`} loading={removeItem.isPending}
          onClick={() => removeItem.mutate(item.id, { onError: toast.error })}>
          <Icon name="trash" className="size-4" />
        </Button>
      )}
    </li>
  );
}
