import { useState } from 'react';
import { Link } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { can } from '@/domain/permissions';
import { useDashboard, type DashboardFilters } from '@/data/hooks';
import type { DashboardDto, StockAlertDto } from '@/data/dto';
import { formatDate, formatMoney, percentChange, toIsoDate } from '@/lib/format';
import { cn } from '@/lib/cn';
import { Badge } from '../components/atoms/Badge';
import { Icon } from '../components/atoms/Icon';
import { Input, Select } from '../components/atoms/Field';
import { Skeleton } from '../components/atoms/Skeleton';
import { ClickBars, DayBars } from '../components/molecules/ClickableCharts';
import { DemoAgentDiary } from '../components/organisms/DemoAgentDiary';
import { StockOverview } from '../components/organisms/StockOverview';
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

// Dashboard (pedido do Douglas, 29/10/2026): o gerente e a equipe técnica veem vendas + estoque; Operações e Compras e o
// estoquista veem só o bloco de estoque, porque faturamento e vendas são só do gerente (a API /crm/dashboard recusa os
// outros papéis com 403, então a tela nem chama).
export function DashboardPage() {
  const { role } = useSession();
  return role && can.dashboard(role) ? <SalesDashboard /> : <StockDashboard />;
}

function StockDashboard() {
  return (
    <>
      <PageHeader
        title="Dashboard"
        help="Situação do estoque agora: quantas peças e quanto vale, o que está zerado ou acaba em 7 dias, o que pedir, os pedidos de compra a caminho e o dinheiro parado. Clique num cartão para ir à tela certa."
        subtitle="Como está o estoque hoje e o que precisa de atenção."
      />
      <StockOverview />
    </>
  );
}

function SalesDashboard() {
  const [preset, setPreset] = useState<Preset>('month');   // padrão da HU28
  const [custom, setCustom] = useState(() => rangeOf('month'));
  const [filters, setFilters] = useState<DashboardFilters>({});
  const [from, to] = preset === 'custom' ? custom : rangeOf(preset);

  // Um dia escolhido que ficou fora do período novo deixa de valer (não precisa de efeito: só não é enviado).
  const day = filters.day && filters.day >= from && filters.day <= to ? filters.day : undefined;
  const active: DashboardFilters = { categoryId: filters.categoryId, productId: filters.productId, day, sellerId: filters.sellerId, channel: filters.channel };
  const query = useDashboard(from, to, active);
  // A lista de vendedores vem na própria resposta; com placeholderData ela não some enquanto recarrega.
  const sellers = query.data?.sellers ?? [];

  // Clicar escolhe; clicar de novo no mesmo item tira o filtro.
  const toggleCategory = (id: number) =>
    // Trocar a categoria limpa o produto: ele pode nem pertencer à categoria nova.
    setFilters((f) => ({ ...f, categoryId: f.categoryId === id ? undefined : id, productId: undefined }));
  const toggleProduct = (id: number) => setFilters((f) => ({ ...f, productId: f.productId === id ? undefined : id }));
  const toggleDay = (date: string) => setFilters((f) => ({ ...f, day: f.day === date ? undefined : date }));
  const pickSeller = (id?: number) => setFilters((f) => ({ ...f, sellerId: id }));
  const clearFilters = () => setFilters({});
  const hasFilters = active.categoryId != null || active.productId != null || active.day != null || active.sellerId != null;
  const pickChannel = (c?: 'STORE' | 'ONLINE') => setFilters((f) => ({ ...f, channel: c }));

  return (
    <>
      <PageHeader
        title="Dashboard" help="Números da loja no período escolhido. Só vendas confirmadas contam; as canceladas aparecem à parte. Clique num gráfico para filtrar o painel."
        subtitle="Clique nos gráficos para filtrar o painel inteiro. Clique de novo para remover o filtro."
      />

      <div className="mb-4 flex flex-wrap items-end gap-2">
        <div className="flex max-w-full flex-wrap gap-1 rounded-lg border border-border bg-surface p-1" role="group" aria-label="Período">
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
        {/* Vendedor(a): quem abriu a venda. "Todos" = a loja inteira. */}
        <Select
          label="Vendedor(a)"
          className="w-56"
          value={filters.sellerId ?? ''}
          onChange={(e) => pickSeller(e.target.value ? Number(e.target.value) : undefined)}
        >
          <option value="">Todos</option>
          {sellers.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
        </Select>
        {/* Canal (spec crm/07): loja física × pedidos do espaço do cliente. */}
        <Select
          label="Canal"
          className="w-44"
          value={filters.channel ?? ''}
          onChange={(e) => pickChannel(e.target.value === 'STORE' || e.target.value === 'ONLINE' ? e.target.value : undefined)}
        >
          <option value="">Todos</option>
          <option value="STORE">Loja física</option>
          <option value="ONLINE">Online</option>
        </Select>
      </div>

      <QueryState
        query={query}
        skeleton={
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-24" />)}
          </div>
        }
      >
        {(d) => (
          <DashboardBody
            d={d}
            hasFilters={hasFilters}
            onCategory={toggleCategory}
            onProduct={toggleProduct}
            onDay={toggleDay}
            onSeller={pickSeller}
            onClear={clearFilters}
          />
        )}
      </QueryState>

      <div className="mt-8">
        <StockOverview />
      </div>
    </>
  );
}

function FilterChip({ label, value, onRemove }: { label: string; value: string; onRemove: () => void }) {
  return (
    <button
      type="button"
      onClick={onRemove}
      aria-label={`Remover filtro ${label}: ${value}`}
      className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-primary/40 bg-primary-soft py-1 pr-2 pl-3 text-sm text-primary hover:bg-primary-soft/70"
    >
      <span className="truncate"><span className="text-muted">{label}:</span> <strong>{value}</strong></span>
      <Icon name="close" className="size-3.5 shrink-0" />
    </button>
  );
}

function DashboardBody({ d, hasFilters, onCategory, onProduct, onDay, onSeller, onClear }: {
  d: DashboardDto;
  hasFilters: boolean;
  onCategory: (id: number) => void;
  onProduct: (id: number) => void;
  onDay: (date: string) => void;
  onSeller: (id?: number) => void;
  onClear: () => void;
}) {
  const sel = d.selected;
  // Links dos cartões levam à lista de vendas já com o mesmo período.
  const salesLink = (status: string) => `/vendas?status=${status}&from=${d.period.from}&to=${d.period.to}`;
  return (
    <div className="flex flex-col gap-4">
      {hasFilters && (
        <div className="flex flex-wrap items-center gap-2" aria-label="Filtros ativos">
          <span className="text-sm text-muted">Filtrando por:</span>
          {sel.categoryId != null && <FilterChip label="Categoria" value={sel.categoryName ?? `#${sel.categoryId}`} onRemove={() => onCategory(sel.categoryId!)} />}
          {sel.productId != null && <FilterChip label="Produto" value={sel.productName ?? `#${sel.productId}`} onRemove={() => onProduct(sel.productId!)} />}
          {sel.day != null && <FilterChip label="Dia" value={formatDate(sel.day)} onRemove={() => onDay(sel.day!)} />}
          {sel.sellerId != null && <FilterChip label="Vendedor(a)" value={sel.sellerName ?? `#${sel.sellerId}`} onRemove={() => onSeller(undefined)} />}
          <button type="button" onClick={onClear} className="text-sm font-medium text-primary hover:underline">Limpar filtros</button>
        </div>
      )}

      <p className="text-xs text-muted">
        Período: {formatDate(d.period.from)}{d.period.from !== d.period.to && ` a ${formatDate(d.period.to)}`} · comparado com{' '}
        {formatDate(d.previousPeriod.from)}{d.previousPeriod.from !== d.previousPeriod.to && ` a ${formatDate(d.previousPeriod.to)}`}
      </p>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <KpiCard label="Clientes cadastrados" value={d.totalCustomers} to="/clientes" />
        <KpiCard label="Produtos cadastrados" value={d.totalProducts} to="/produtos" />
        <KpiCard label="Vendas confirmadas" value={d.confirmedSales} change={percentChange(d.confirmedSales, d.previousPeriod.confirmedSales)} to={salesLink('CONFIRMED')} />
        <KpiCard label="Faturamento" value={formatMoney(d.revenue)} change={percentChange(d.revenue, d.previousPeriod.revenue)} to={salesLink('CONFIRMED')} />
        <KpiCard label="Ticket médio" value={formatMoney(d.averageTicket)} />
        <KpiCard label="Vendas canceladas" value={d.cancelledSales} tone={d.cancelledSales > 0 ? 'warning' : 'default'} hint="Não entram no faturamento" to={salesLink('CANCELLED')} />
      </div>

      <Card title="Faturamento por dia" actions={<span className="hidden text-xs text-muted sm:inline">Clique numa barra para ver só aquele dia</span>}>
        <DayBars data={d.revenueByDay} selected={sel.day} onPick={onDay} />
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Mais vendidos (top 5)">
          <ClickBars
            empty="Nenhuma venda confirmada no período."
            selectedId={sel.productId}
            onPick={onProduct}
            rows={d.topProducts.map((p) => ({ id: p.productId, label: p.name, sub: p.code, value: p.quantity, display: `${p.quantity} un.` }))}
          />
        </Card>
        <Card title="Vendas por categoria">
          <ClickBars
            empty="Nenhuma venda confirmada no período."
            selectedId={sel.categoryId}
            onPick={onCategory}
            rows={d.salesByCategory.map((c) => ({ id: c.categoryId, label: c.category, sub: `${c.quantity} un.`, value: c.revenue, display: formatMoney(c.revenue) }))}
          />
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <StockList title="Estoque baixo" items={d.lowStock} tone="warning" empty="Nenhum produto abaixo do mínimo." />
        <StockList title="Sem estoque" items={d.outOfStock} tone="error" empty="Nenhum produto zerado." />
      </div>

      <DemoAgentDiary />
    </div>
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
            <li key={p.productId} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-4 py-2.5 text-sm">
              <Link to={`/produtos/${p.productId}`} className="min-w-0 truncate hover:underline">
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
