import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { movementLabel } from '@/data/adapters';
import { useProduct, useProductActions } from '@/data/hooks';
import { ApiError } from '@/data/http';
import type { ProductDetailDto, StockMovementDto } from '@/data/dto';
import { can } from '@/domain/permissions';
import { formatDateTime, formatMoney } from '@/lib/format';
import { Button } from '../../components/atoms/Button';
import { ConfirmDialog } from '../../components/molecules/ConfirmDialog';
import { KpiCard } from '../../components/molecules/KpiCard';
import { QueryState } from '../../components/molecules/QueryState';
import { DataTable } from '../../components/organisms/DataTable';
import { Card, Detail, PageHeader } from '../../components/templates/Page';
import { ProductGallery } from '../../components/organisms/ProductGallery';
import { ProductFormModal } from './ProductFormModal';
import { StockBadge } from './ProductsPage';

export function ProductDetailPage() {
  const query = useProduct(Number(useParams().id));
  return <QueryState query={query}>{(d) => <ProductDetail d={d} />}</QueryState>;
}

export function MovementQty({ m }: { m: StockMovementDto }) {
  return (
    <span className={`tabular font-medium ${m.quantity > 0 ? 'text-success' : 'text-error'}`}>
      {m.quantity > 0 ? '+' : ''}{m.quantity}
    </span>
  );
}

function ProductDetail({ d }: { d: ProductDetailDto }) {
  const p = d.product;
  const { role } = useSession();
  const toast = useToast();
  const navigate = useNavigate();
  const { remove, setActive } = useProductActions();
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [offerDeactivate, setOfferDeactivate] = useState<string | null>(null);

  async function doDelete() {
    try {
      await remove.mutateAsync(p.id);
      toast.success('Pronto! O produto foi removido.');
      navigate('/produtos');
    } catch (err) {
      setConfirmDelete(false);
      if (err instanceof ApiError && err.code === 'product_has_history') setOfferDeactivate(err.message);
      else toast.error(err);
    }
  }

  async function toggle(active: boolean) {
    try {
      await setActive.mutateAsync({ id: p.id, active });
      setOfferDeactivate(null);
      toast.success(active ? 'Produto reativado.' : 'Produto desativado: não aparece mais para venda.');
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <>
      <PageHeader
        title={p.name} help="Ficha do produto, últimas movimentações de estoque e quanto já vendeu. O estoque só muda pela tela Estoque ou por vendas."
        subtitle={`${p.code} · ${p.categoryName}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => navigate('/produtos')}>Voltar</Button>
            {role && can.stock(role) && <Button variant="secondary" onClick={() => navigate(`/estoque?entrada=${p.id}`)}>Movimentar estoque</Button>}
            {/* Spec crm/09: peça zerada → o vendedor registra que o cliente queria. */}
            {role && can.openRequest(role) && p.stockQty === 0 && (
              <Button variant="secondary" onClick={() => navigate(`/chamados?novo=${p.id}`)}>Cliente quer esta peça</Button>
            )}
            {role && can.manageProducts(role) && <Button variant="secondary" onClick={() => setEditing(true)}>Editar</Button>}
            {role && can.deleteProduct(role) && (p.active
              ? <Button variant="secondary" onClick={() => setConfirmDelete(true)}>Excluir</Button>
              : <Button variant="secondary" loading={setActive.isPending} onClick={() => void toggle(true)}>Reativar</Button>)}
          </>
        }
      />
      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Fotos">
          <div className="p-4"><ProductGallery productId={p.id} name={p.name} editable={!!role && can.manageProducts(role)} /></div>
        </Card>
        <Card title="Dados" className="lg:col-span-2">
          <dl className="grid grid-cols-2 gap-4 p-4 sm:grid-cols-3">
            <Detail label="Situação"><StockBadge p={p} /></Detail>
            <Detail label="Preço">{formatMoney(p.price)}</Detail>
            <Detail label="Tamanho / cor">{p.size} · {p.color}</Detail>
            <Detail label="Categoria"><Link className="text-primary hover:underline" to={`/produtos?categoria=${p.categoryId}`}>{p.categoryName}</Link></Detail>
            <Detail label="Estoque mínimo">{p.minStock}</Detail>
            <Detail label="Descrição">{p.description ?? '—'}</Detail>
          </dl>
        </Card>
        <div className="grid grid-cols-2 gap-3 lg:col-span-3 lg:grid-cols-2">
          <KpiCard label="Em estoque" value={p.stockQty} tone={p.stockQty === 0 ? 'error' : p.lowStock ? 'warning' : 'default'} />
          <KpiCard label="Total vendido" value={`${d.totalSold} un.`} hint="Em vendas confirmadas" />
        </div>
      </div>

      <Card title="Últimas movimentações" className="mt-4">
        {d.recentMovements.length === 0 ? (
          <p className="p-6 text-center text-sm text-muted">Sem movimentações.</p>
        ) : (
          <DataTable<StockMovementDto>
            rows={d.recentMovements}
            rowKey={(m) => m.id}
            columns={[
              { header: 'Data', cell: (m) => formatDateTime(m.createdAt) },
              { header: 'Tipo', cell: (m) => movementLabel[m.type] },
              { header: 'Qtd.', cell: (m) => <MovementQty m={m} />, align: 'right' },
              { header: 'Saldo após', cell: (m) => <span className="tabular">{m.balanceAfter}</span>, align: 'right' },
              { header: 'Origem', cell: (m) => m.saleId ? <Link className="text-primary hover:underline" to={`/vendas/${m.saleId}`}>Venda #{m.saleId}</Link> : (m.reason ?? '—') },
            ]}
          />
        )}
      </Card>

      {editing && <ProductFormModal product={p} onClose={() => setEditing(false)} />}
      <ConfirmDialog open={confirmDelete} title="Excluir produto" danger confirmLabel="Excluir" loading={remove.isPending}
        message={<>Excluir <strong>{p.name}</strong>? Só é possível se ele não tiver vendas nem movimentações além do estoque inicial.</>}
        onConfirm={() => void doDelete()} onClose={() => setConfirmDelete(false)} />
      <ConfirmDialog open={!!offerDeactivate} title="Não é possível excluir" confirmLabel="Desativar produto" loading={setActive.isPending}
        message={offerDeactivate} onConfirm={() => void toggle(false)} onClose={() => setOfferDeactivate(null)} />
    </>
  );
}
