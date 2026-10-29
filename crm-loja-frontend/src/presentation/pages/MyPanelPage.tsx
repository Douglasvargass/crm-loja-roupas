import { useState } from 'react';
import { Link } from 'react-router';
import { customerLabel, saleStatusLabel, saleStatusTone, targetStatusLabel, targetStatusTone } from '@/data/adapters';
import type { SellerPanelDto } from '@/data/dto';
import { useMyPanel } from '@/data/hooks';
import { cn } from '@/lib/cn';
import { formatDate, formatDateTime, formatMoney, toIsoDate } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Input } from '../components/atoms/Field';
import { Skeleton } from '../components/atoms/Skeleton';
import { DayBars } from '../components/molecules/ClickableCharts';
import { KpiCard } from '../components/molecules/KpiCard';
import { QueryState } from '../components/molecules/QueryState';
import { Card, PageHeader } from '../components/templates/Page';

const currentMonth = () => toIsoDate(new Date()).slice(0, 7);

// Spec crm/06: o painel do vendedor. Só os números dele (as vendas que ele abriu) e a meta dele. Sem ranking e
// sem nada da loja inteira: quem compara a equipe é o gerente, na tela Metas.
export function MyPanelPage() {
  const [month, setMonth] = useState(currentMonth);
  const query = useMyPanel(month);
  return (
    <>
      <PageHeader
        title="Meu painel" help="Suas vendas confirmadas no mês e a sua meta. Dia útil é de segunda a sábado; a projeção mostra onde você fecha o mês no ritmo atual."
        subtitle="Suas vendas e a sua meta do mês."
        actions={
          <Input
            label="Mês"
            type="month"
            className="w-44"
            value={month}
            max={currentMonth()}
            onChange={(e) => e.target.value && setMonth(e.target.value)}
          />
        }
      />
      <p className="mb-4 rounded-lg border border-info/30 bg-info-soft px-3 py-2 text-sm text-info">
        Você vê apenas os seus próprios números: as vendas que você abriu e a sua meta. Ninguém da equipe aparece aqui.
      </p>
      <QueryState
        query={query}
        skeleton={
          <div className="grid gap-3">
            <Skeleton className="h-32" />
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-24" />)}
            </div>
          </div>
        }
      >
        {(p) => <PanelBody p={p} />}
      </QueryState>
    </>
  );
}

function PanelBody({ p }: { p: SellerPanelDto }) {
  const [day, setDay] = useState<string | null>(null);
  const picked = day ? p.revenueByDay.find((d) => d.date === day) : undefined;
  return (
    <div className="flex flex-col gap-4">
      <TargetCard p={p} />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <KpiCard label={p.closed ? 'Vendido no mês' : 'Vendido até hoje'} value={formatMoney(p.revenue)} />
        <KpiCard label="Vendas confirmadas" value={p.confirmedSales} hint={`Ticket médio ${formatMoney(p.averageTicket)}`} />
        {p.today ? (
          <KpiCard label="Hoje" value={formatMoney(p.today.revenue)} hint={`${p.today.sales} venda(s) confirmada(s)`} />
        ) : (
          <KpiCard label="Média por dia útil" value={formatMoney(p.perBusinessDay)} />
        )}
        <KpiCard
          label="Canceladas"
          value={p.cancelledSales}
          tone={p.cancelledSales > 0 ? 'warning' : 'default'}
          hint="Não contam para a meta"
        />
      </div>

      <Card title="Minhas vendas por dia" actions={<span className="hidden text-xs text-muted sm:inline">Clique num dia para ver o total dele</span>}>
        <DayBars data={p.revenueByDay} selected={day} onPick={(d) => setDay((cur) => (cur === d ? null : d))} />
        {picked && (
          <p className="border-t border-border px-4 py-2.5 text-sm">
            <strong>{formatDate(picked.date)}</strong>: {formatMoney(picked.revenue)} em {picked.sales} venda(s).
          </p>
        )}
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="O que eu mais vendi">
          {p.topProducts.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">Nenhuma venda confirmada neste mês ainda.</p>
          ) : (
            <ol className="divide-y divide-border">
              {p.topProducts.map((t, i) => (
                <li key={t.productId} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
                  <span className="min-w-0 truncate">
                    <span className="mr-2 text-muted">{i + 1}.</span>
                    {t.name} <span className="text-xs text-muted">{t.code}</span>
                  </span>
                  <span className="tabular shrink-0 text-muted">{t.quantity} un. · {formatMoney(t.revenue)}</span>
                </li>
              ))}
            </ol>
          )}
        </Card>

        <Card title="Minhas últimas vendas">
          {p.recentSales.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">Você ainda não abriu vendas neste mês.</p>
          ) : (
            <ul className="divide-y divide-border">
              {p.recentSales.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-4 py-2.5 text-sm">
                  <Link to={`/vendas/${s.id}`} className="min-w-0 truncate hover:underline">
                    <span className="tabular font-medium">#{s.id}</span>{' '}
                    <span className={s.customerName ? '' : 'text-muted'}>{customerLabel(s.customerName)}</span>
                    <span className="block text-xs text-muted">{formatDateTime(s.confirmedAt ?? s.createdAt)}</span>
                  </Link>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className="tabular">{formatMoney(s.total)}</span>
                    <Badge tone={saleStatusTone[s.status]}>{saleStatusLabel[s.status]}</Badge>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

// O cartão da meta: barra de progresso e frases que dizem o que fazer, em português simples.
function TargetCard({ p }: { p: SellerPanelDto }) {
  if (p.target == null) {
    return (
      <Card>
        <div className="flex flex-col gap-1 p-4">
          <span className="text-sm text-muted">Minha meta</span>
          <p className="text-base font-medium">Sua meta {p.closed ? 'daquele' : 'deste'} mês ainda não foi definida. Fale com o gerente.</p>
          <p className="text-sm text-muted">
            {p.closed
              ? `Você vendeu ${formatMoney(p.revenue)} no mês.`
              : `No ritmo atual (${formatMoney(p.perBusinessDay)} por dia útil), você fecha o mês em ${formatMoney(p.projection)}.`}
          </p>
        </div>
      </Card>
    );
  }
  const pct = p.percent ?? 0;
  return (
    <Card>
      <div className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-sm text-muted">Minha meta {p.closed ? 'do mês' : 'deste mês'}</span>
          <Badge tone={targetStatusTone[p.status]}>{targetStatusLabel[p.status]}</Badge>
        </div>
        <p className="text-2xl font-semibold tracking-tight">
          <span className="tabular">{formatMoney(p.revenue)}</span>
          <span className="text-base font-normal text-muted"> de {formatMoney(p.target)}</span>
        </p>
        <div
          className="h-3 overflow-hidden rounded-full bg-surface-2"
          role="progressbar"
          aria-label="Quanto da meta já foi vendido"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.min(100, Math.round(pct))}
        >
          <div
            className={cn('h-full rounded-full transition-[width]', pct >= 100 ? 'bg-success' : 'bg-primary')}
            style={{ width: `${Math.min(100, pct)}%` }}
          />
        </div>
        <ul className="flex flex-col gap-1 text-sm">
          {phrases(p).map((f) => <li key={f}>{f}</li>)}
        </ul>
      </div>
    </Card>
  );
}

function phrases(p: SellerPanelDto): string[] {
  const pct = `${(p.percent ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
  if (p.closed) {
    return [p.status === 'ACHIEVED'
      ? `Mês fechado: meta batida, com ${pct} da meta.`
      : `Mês fechado: você vendeu ${pct} da meta. Faltaram ${formatMoney(p.remaining ?? 0)}.`];
  }
  const out: string[] = [];
  if (p.status === 'ACHIEVED') {
    out.push(`Meta batida! Você já vendeu ${pct} da meta. Cada venda a mais é lucro para a loja.`);
  } else if (p.businessDaysLeft === 0) {
    out.push(`Hoje é o último dia útil do mês: faltam ${formatMoney(p.remaining ?? 0)} para a meta.`);
  } else {
    out.push(`Faltam ${formatMoney(p.remaining ?? 0)} em ${p.businessDaysLeft} dia(s) útil(eis): ${formatMoney(p.neededPerDay ?? 0)} por dia útil.`);
  }
  if (p.businessDaysElapsed > 0) {
    out.push(`No ritmo atual (${formatMoney(p.perBusinessDay)} por dia útil), você fecha o mês em ${formatMoney(p.projection)}.`);
  }
  out.push('Dia útil = segunda a sábado. Venda de domingo também conta.');
  return out;
}
