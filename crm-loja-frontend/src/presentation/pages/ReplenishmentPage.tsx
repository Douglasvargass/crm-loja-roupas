import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { coverageLabel, coverageTone } from '@/data/adapters';
import type { PlanningItemDto } from '@/data/dto';
import { usePlanning } from '@/data/hooks';
import { can } from '@/domain/permissions';
import { cn } from '@/lib/cn';
import { formatDate, formatMoney } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Select } from '../components/atoms/Field';
import { Icon } from '../components/atoms/Icon';
import { KpiCard } from '../components/molecules/KpiCard';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

type View = 'attention' | 'order' | 'stale' | 'all';

const VIEWS: { id: View; label: string }[] = [
  { id: 'attention', label: 'Precisa de atenção' },
  { id: 'order', label: 'Para pedir' },
  { id: 'stale', label: 'Parados' },
  { id: 'all', label: 'Todos' },
];

const HORIZONS = [15, 30, 45, 60];

const fits = (v: View, i: PlanningItemDto) =>
  v === 'all' ? true
    : v === 'order' ? i.needsOrder
      : v === 'stale' ? i.status === 'STALE'
        : i.status === 'OUT' || i.status === 'CRITICAL' || i.status === 'ATTENTION';

// Spec crm/11: painel "O que vai faltar". A conta é do back-end (Coverage); a tela só filtra e mostra.
export function ReplenishmentPage() {
  const { role } = useSession();
  const navigate = useNavigate();
  const [horizon, setHorizon] = useState(30);
  const [view, setView] = useState<View>('attention');
  const [search, setSearch] = useState('');
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const query = usePlanning(horizon);
  const buyer = role ? can.buy(role) : false;

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (query.data?.items ?? []).filter((i) => fits(view, i)
      && (!term || i.name.toLowerCase().includes(term) || i.code.toLowerCase().includes(term)));
  }, [query.data, view, search]);

  // Leva para o pedido de compra já montado: "id:quantidade" de cada peça escolhida.
  function order(items: PlanningItemDto[]) {
    const list = items.map((i) => `${i.productId}:${Math.max(1, i.suggestedQty)}`).join(',');
    navigate(`/pedidos-compra?novo=${encodeURIComponent(list)}`);
  }

  function toggle(id: number) {
    setPicked((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id); else n.add(id);
      return n;
    });
  }

  const s = query.data?.summary;
  const pickedItems = (query.data?.items ?? []).filter((i) => picked.has(i.productId));

  return (
    <>
      <PageHeader
        title="Reposição"
        help={'O que vai faltar. Para cada peça: quanto vende por dia (média dos últimos 30 dias), em quantos dias acaba, '
          + 'o que já está a caminho e quanto pedir. Quanto pedir = o que deve vender no período escolhido + estoque mínimo '
          + '+ chamados abertos da peça, menos o estoque e o que já foi pedido.'}
        subtitle={buyer
          ? 'Veja o que acaba primeiro e monte o pedido de compra com um clique.'
          : 'Veja o que acaba primeiro. Quem faz o pedido de compra é Operações e Compras ou o gerente.'}
        actions={
          <Select label="Pedir para cobrir" value={String(horizon)} onChange={(e) => setHorizon(Number(e.target.value))}>
            {HORIZONS.map((h) => <option key={h} value={h}>{h} dias</option>)}
          </Select>
        }
      />

      {s && (
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
          <KpiCard label="Zerados" value={s.outOfStock} tone={s.outOfStock > 0 ? 'error' : 'default'} hint="peças com estoque 0" />
          <KpiCard label="Acabam em 7 dias" value={s.critical} tone={s.critical > 0 ? 'error' : 'default'} hint="ou já no estoque mínimo" />
          <KpiCard label="Para pedir" value={s.toOrder} tone={s.toOrder > 0 ? 'warning' : 'default'} hint="o que está a caminho não basta" />
          <KpiCard label="Pedidos atrasados" value={s.lateOrders} tone={s.lateOrders > 0 ? 'error' : 'default'} to="/pedidos-compra?atrasados=1"
            hint={`${s.openOrders} pedido(s) a caminho`} />
          <KpiCard label="Chegam em 7 dias" value={s.arrivingThisWeek} to="/pedidos-compra" hint="pedidos com data nesta semana" />
          <KpiCard label="Dinheiro parado" value={formatMoney(s.staleValue)} hint={`${s.stale} peça(s) sem venda em 30 dias`} />
        </div>
      )}

      <Card>
        <div className="flex flex-wrap items-end gap-2 border-b border-border p-2">
          <div className="flex flex-wrap gap-1" role="group" aria-label="Mostrar">
            {VIEWS.map((v) => (
              <button
                key={v.id}
                type="button"
                aria-pressed={view === v.id}
                onClick={() => setView(v.id)}
                className={cn('rounded-md px-3 py-1.5 text-sm font-medium', view === v.id ? 'bg-primary text-on-primary' : 'text-muted hover:bg-surface-2')}
              >
                {v.label}
              </button>
            ))}
          </div>
          <div className="ml-auto flex flex-wrap items-end gap-2">
            <Input label="Buscar peça" placeholder="Nome ou código" value={search} onChange={(e) => setSearch(e.target.value)} />
            {buyer && (
              <Button onClick={() => order(pickedItems)} disabled={pickedItems.length === 0}
                title={pickedItems.length === 0 ? 'Marque as peças na primeira coluna para montar um pedido com elas.' : undefined}>
                <Icon name="truck" className="size-4" />Pedir marcadas ({pickedItems.length})
              </Button>
            )}
          </div>
        </div>
        {buyer && pickedItems.length === 0 && (
          <p className="px-3 pt-2 text-xs text-muted">Dica: marque várias peças do mesmo fornecedor e clique em "Pedir marcadas" para montar um pedido só.</p>
        )}
        <QueryState
          query={query}
          isEmpty={() => rows.length === 0}
          empty={view === 'attention' ? 'Nenhuma peça acabando. Tudo em ordem!' : view === 'stale' ? 'Nenhuma peça parada.' : 'Nada aqui.'}
        >
          {() => (
            <DataTable<PlanningItemDto>
              rows={rows}
              rowKey={(i) => i.productId}
              columns={[
                ...(buyer ? [{
                  header: '',
                  cell: (i: PlanningItemDto) => (
                    <input type="checkbox" className="size-4 accent-primary" checked={picked.has(i.productId)}
                      onChange={() => toggle(i.productId)} aria-label={`Marcar ${i.name}`} />
                  ),
                }] : []),
                {
                  header: 'Peça',
                  cell: (i) => (
                    <span>
                      <span className="font-medium">{i.name}</span>
                      <span className="block text-xs text-muted">{[i.code, `tam. ${i.size}`, i.color, i.categoryName].join(' · ')}</span>
                    </span>
                  ),
                },
                {
                  header: 'Estoque',
                  align: 'right',
                  cell: (i) => (
                    <span className="tabular">
                      {i.stock}
                      <span className="block text-xs text-muted">mín. {i.minStock}</span>
                    </span>
                  ),
                },
                {
                  header: 'Vende por dia',
                  align: 'right',
                  cell: (i) => (
                    <span className="tabular">
                      {i.dailyAvg.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}
                      <span className="block text-xs text-muted">{i.soldInWindow} em 30 dias</span>
                    </span>
                  ),
                },
                {
                  header: 'Acaba em',
                  cell: (i) => i.stock === 0 ? <span className="text-error">já acabou</span>
                    : i.daysLeft == null ? <span className="text-xs text-muted">sem venda para prever</span>
                      : (
                        <span>
                          {i.daysLeft === 0 ? 'hoje' : `${i.daysLeft} dia(s)`}
                          {i.runOutDate && <span className="block text-xs text-muted">{formatDate(i.runOutDate)}</span>}
                        </span>
                      ),
                },
                {
                  header: 'A caminho',
                  cell: (i) => i.incoming === 0 ? <span className="text-muted">—</span> : (
                    <span>
                      {i.incoming} un.
                      {i.nextArrival && <span className="block text-xs text-muted">chega {formatDate(i.nextArrival)}</span>}
                      {i.arrivesAfterRunOut && <span className="block text-xs font-medium text-error">chega depois de acabar</span>}
                      {!i.arrivesAfterRunOut && i.covered && <span className="block text-xs font-medium text-success">já resolve</span>}
                    </span>
                  ),
                },
                {
                  header: 'Chamados',
                  align: 'right',
                  cell: (i) => i.openRequestsQty > 0 ? <span className="font-medium text-warning">{i.openRequestsQty} un.</span> : <span className="text-muted">—</span>,
                },
                {
                  header: 'Pedir',
                  align: 'right',
                  cell: (i) => i.suggestedQty > 0 ? <span className="tabular font-semibold">{i.suggestedQty}</span> : <span className="text-muted">0</span>,
                },
                { header: 'Situação', cell: (i) => <Badge tone={coverageTone[i.status]}>{coverageLabel[i.status]}</Badge> },
                ...(buyer ? [{
                  header: 'Ações',
                  align: 'right' as const,
                  cell: (i: PlanningItemDto) => i.suggestedQty > 0
                    ? <Button size="sm" variant="secondary" onClick={() => order([i])}>Pedir</Button>
                    : <span className="text-xs text-muted">{i.status === 'STALE' ? 'parada: não pedir' : 'não precisa'}</span>,
                }] : []),
              ]}
            />
          )}
        </QueryState>
      </Card>
    </>
  );
}
