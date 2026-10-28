import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useDashboard } from '@/data/hooks';
import type { DashboardDto, StockAlertDto } from '@/data/dto';
import { formatDate, formatMoney, percentChange, toIsoDate } from '@/lib/format';
import { cn } from '@/lib/cn';
import { Badge } from '../components/atoms/Badge';
import { Input } from '../components/atoms/Field';
import { Skeleton } from '../components/atoms/Skeleton';
import { KpiCard } from '../components/molecules/KpiCard';
import { QueryState } from '../components/molecules/QueryState';
import { Card, PageHeader } from '../components/templates/Page';

type Preset = 'today' | '7d' | '30d' | 'month' | 'custom';

const PRESETS: { id: Preset; label: string }[] = [
  { id: 'today', label: 'Hoje' },
  { id: '7d', label: '7 dias' },
  { id: '30d', label: '30 dias' },
  { id: 'month', label: 'Este mês' },
  { id: 'custom', label: 'Personalizado' },
];

function rangeOf(p: Exclude<Preset, 'custom'>): [string, string] {
  const today = new Date();
  const from = new Date(today);
  if (p === '7d') from.setDate(today.getDate() - 6);
  if (p === '30d') from.setDate(today.getDate() - 29);
  if (p === 'month') from.setDate(1);
  return [toIsoDate(from), toIsoDate(today)];
}

export function DashboardPage() {
  const [preset, setPreset] = useState<Preset>('month');   // padrão da HU28
  const [custom, setCustom] = useState(() => rangeOf('month'));
  const [from, to] = preset === 'custom' ? custom : rangeOf(preset);
  const query = useDashboard(from, to);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Indicadores calculados a partir dos dados reais da loja." />

      <div className="mb-5 flex flex-wrap items-end gap-2">
        <div className="flex flex-wrap gap-1 rounded-lg border border-border bg-surface p-1" role="group" aria-label="Período">
          {PRESETS.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => setPreset(p.id)}
              aria-pressed={preset === p.id}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm font-medium',
                preset === p.id ? 'bg-primary text-on-primary' : 'text-muted hover:bg-surface-2',
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
        {preset === 'custom' && (
          <div className="flex gap-2">
            <Input label="De" type="date" value={custom[0]} max={custom[1]} onChange={(e) => setCustom([e.target.value, custom[1]])} />
            <Input label="Até" type="date" value={custom[1]} min={custom[0]} onChange={(e) => setCustom([custom[0], e.target.value])} />
          </div>
        )}
      </div>

      <QueryState
        query={query}
        skeleton={
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-24" />)}
          </div>
        }
      >
        {(d) => <DashboardBody d={d} />}
      </QueryState>
    </>
  );
}

function DashboardBody({ d }: { d: DashboardDto }) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-xs text-muted">
        Período: {formatDate(d.period.from)} a {formatDate(d.period.to)} · comparado com {formatDate(d.previousPeriod.from)} a{' '}
        {formatDate(d.previousPeriod.to)}
      </p>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <KpiCard label="Clientes cadastrados" value={d.totalCustomers} />
        <KpiCard label="Produtos cadastrados" value={d.totalProducts} />
        <KpiCard label="Vendas confirmadas" value={d.confirmedSales} change={percentChange(d.confirmedSales, d.previousPeriod.confirmedSales)} />
        <KpiCard label="Faturamento" value={formatMoney(d.revenue)} change={percentChange(d.revenue, d.previousPeriod.revenue)} />
        <KpiCard label="Ticket médio" value={formatMoney(d.averageTicket)} />
        <KpiCard label="Vendas canceladas" value={d.cancelledSales} tone={d.cancelledSales > 0 ? 'warning' : 'default'} hint="Não entram no faturamento" />
      </div>

      <Card title="Faturamento por dia">
        <RevenueChart data={d.revenueByDay} />
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Mais vendidos (top 5)">
          {d.topProducts.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">Nenhuma venda confirmada no período.</p>
          ) : (
            <HBars rows={d.topProducts.map((p) => ({ label: p.name, sub: p.code, value: p.quantity, display: `${p.quantity} un.` }))} />
          )}
        </Card>
        <Card title="Vendas por categoria">
          {d.salesByCategory.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">Nenhuma venda confirmada no período.</p>
          ) : (
            <HBars rows={d.salesByCategory.map((c) => ({ label: c.category, sub: `${c.quantity} un.`, value: c.revenue, display: formatMoney(c.revenue) }))} />
          )}
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <StockList title="Estoque baixo" items={d.lowStock} tone="warning" empty="Nenhum produto abaixo do mínimo." />
        <StockList title="Sem estoque" items={d.outOfStock} tone="error" empty="Nenhum produto zerado." />
      </div>
    </div>
  );
}

function RevenueChart({ data }: { data: DashboardDto['revenueByDay'] }) {
  const max = useMemo(() => Math.max(...data.map((x) => x.revenue), 0), [data]);
  if (max === 0) return <p className="p-6 text-center text-sm text-muted">Sem faturamento no período.</p>;
  const showEvery = Math.ceil(data.length / 10);
  return (
    <div className="px-4 pt-4 pb-2">
      <div className="flex h-44 items-end gap-[2px]" role="img" aria-label="Gráfico de faturamento por dia">
        {data.map((x) => (
          <div key={x.date} className="group relative flex h-full flex-1 items-end">
            <div
              className="w-full rounded-t-sm bg-chart transition-opacity group-hover:opacity-80"
              style={{ height: `${Math.max((x.revenue / max) * 100, x.revenue > 0 ? 2 : 0)}%` }}
            />
            <div className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 rounded-md border border-border bg-surface px-2 py-1 text-xs whitespace-nowrap shadow group-hover:block">
              {formatDate(x.date)}: {formatMoney(x.revenue)} · {x.sales} venda(s)
            </div>
          </div>
        ))}
      </div>
      <div className="mt-1 flex gap-[2px] text-[10px] text-muted">
        {data.map((x, i) => (
          <div key={x.date} className="flex-1 truncate text-center">
            {i % showEvery === 0 ? formatDate(x.date).slice(0, 5) : ''}
          </div>
        ))}
      </div>
    </div>
  );
}

function HBars({ rows }: { rows: { label: string; sub: string; value: number; display: string }[] }) {
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <ul className="flex flex-col gap-3 p-4">
      {rows.map((r) => (
        <li key={r.label + r.sub}>
          <div className="mb-1 flex justify-between gap-2 text-sm">
            <span className="truncate">
              {r.label} <span className="text-xs text-muted">{r.sub}</span>
            </span>
            <span className="tabular font-medium">{r.display}</span>
          </div>
          <div className="h-2 rounded-full bg-surface-2">
            <div className="h-2 rounded-full bg-chart" style={{ width: `${(r.value / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

function StockList({ title, items, tone, empty }: { title: string; items: StockAlertDto[]; tone: 'warning' | 'error'; empty: string }) {
  return (
    <Card title={title} actions={<Badge tone={tone}>{items.length}</Badge>}>
      {items.length === 0 ? (
        <p className="p-6 text-center text-sm text-muted">{empty}</p>
      ) : (
        <ul className="divide-y divide-border">
          {items.map((p) => (
            <li key={p.productId} className="flex items-center justify-between gap-2 px-4 py-2.5 text-sm">
              <Link to={`/produtos/${p.productId}`} className="truncate hover:underline">
                {p.name} <span className="text-xs text-muted">{p.code}</span>
              </Link>
              <span className="flex shrink-0 items-center gap-3">
                <span className="tabular text-muted">
                  {p.stockQty} / mín. {p.minStock}
                </span>
                <Link to={`/estoque?entrada=${p.productId}`} className="text-xs font-medium text-primary hover:underline">
                  Registrar entrada
                </Link>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
