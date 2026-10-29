import { useMemo, useState } from 'react';
import type { ForecastDayDto, ForecastModelDto, ForecastPeriodDto, SalesForecastDto } from '@/data/dto';
import { useForecast } from '@/data/hooks';
import { cn } from '@/lib/cn';
import { formatDate, formatMoney } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Select } from '../components/atoms/Field';
import { Skeleton } from '../components/atoms/Skeleton';
import { QueryState } from '../components/molecules/QueryState';
import { Card, PageHeader } from '../components/templates/Page';

// Spec crm/12: quanto a loja deve vender, por período, com a sazonalidade explicada em palavras.
export function ForecastPage() {
  const [months, setMonths] = useState(3);
  const query = useForecast(months);
  return (
    <div className="cores-dashboard">
      <PageHeader
        title="Previsão de vendas"
        help="A previsão de cada dia = nível atual × dia da semana (ou feriado) × semana do salário × data comercial × mês do ano. Cada fator é aprendido com as vendas confirmadas; o que ainda não tem histórico usa a estimativa padrão do varejo. A faixa mostra onde o valor deve cair em cerca de 8 a cada 10 vezes."
        subtitle="Quanto a loja deve vender nos próximos dias e meses, já contando a sazonalidade."
        actions={
          <Select label="Meses à frente" className="w-40" value={months} onChange={(e) => setMonths(Number(e.target.value))}>
            <option value={3}>3 meses</option>
            <option value={6}>6 meses</option>
            <option value={12}>12 meses</option>
          </Select>
        }
      />
      <QueryState query={query} skeleton={<Skeleton className="h-96" />}>
        {(f) => <Body f={f} />}
      </QueryState>
    </div>
  );
}

function Body({ f }: { f: SalesForecastDto }) {
  return (
    <div className="flex flex-col gap-6">
      <p className="text-xs text-muted">
        Com base em {f.historyDays} dias de vendas, desde {formatDate(f.historyFrom)}. Só vendas confirmadas, dos dois canais.
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {f.periods.map((p) => <PeriodCard key={p.key} p={p} />)}
      </div>
      <Card title="Últimos 28 dias e próximos 60" actions={<span className="hidden text-xs text-muted sm:inline">Passe o mouse numa barra para ver o dia</span>}>
        <Timeline days={f.days} today={f.today} />
      </Card>
      <Explain m={f.model} />
    </div>
  );
}

function PeriodCard({ p }: { p: ForecastPeriodDto }) {
  return (
    <div className="flex flex-col gap-1 rounded-xl border border-border bg-surface p-4">
      <span className="text-xs font-medium tracking-wide text-muted uppercase">{p.label}</span>
      <span className="tabular text-2xl font-medium tracking-tight">{formatMoney(p.revenue)}</span>
      <span className="text-xs text-muted">
        entre {formatMoney(p.low)} e {formatMoney(p.high)} · cerca de {p.sales.toLocaleString('pt-BR')} vendas
      </span>
      {p.realized != null && <span className="text-xs text-muted">já vendido até ontem: {formatMoney(p.realized)}</span>}
    </div>
  );
}

// Barras do realizado (claras) e da previsão (fortes), com a divisa de hoje e as datas especiais marcadas.
function Timeline({ days, today }: { days: ForecastDayDto[]; today: string }) {
  const max = useMemo(() => Math.max(...days.map((d) => d.actual ?? d.high ?? d.revenue ?? 0), 1), [days]);
  const labelEvery = Math.ceil(days.length / 10);
  return (
    <div className="px-3 pt-4 pb-2 sm:px-4">
      <div className="flex h-48 items-end gap-px" role="img" aria-label="Faturamento realizado nos últimos 28 dias e previsto para os próximos 60">
        {days.map((d, i) => {
          const value = d.actual ?? d.revenue ?? 0;
          const future = d.actual == null;
          const special = d.holiday || d.events.length > 0;
          const tip = i < days.length / 3 ? 'left-0' : i > (days.length * 2) / 3 ? 'right-0' : 'left-1/2 -translate-x-1/2';
          return (
            <div key={d.date} className={cn('group relative flex h-full min-w-0 flex-1 items-end', d.date === today && 'border-l border-dashed border-text/40 pl-px')}>
              <span
                className={cn('block w-full rounded-t-sm', future ? 'bg-chart' : 'bg-chart-2')}
                style={{ height: `${Math.max((value / max) * 100, value > 0 ? 1.5 : 0)}%` }}
              />
              {special && <span className="absolute -top-0.5 left-1/2 size-1.5 -translate-x-1/2 rounded-full bg-warning" aria-hidden />}
              <span className={cn('pointer-events-none absolute bottom-full z-10 mb-2 hidden rounded-md border border-border bg-surface px-2 py-1 text-xs whitespace-nowrap shadow group-hover:block', tip)}>
                {formatDate(d.date)}: {future ? `previsto ${formatMoney(value)}` : `vendido ${formatMoney(value)}`}
                {future && d.low != null && d.high != null && <> (entre {formatMoney(d.low)} e {formatMoney(d.high)})</>}
                {d.holiday && ' · feriado'}
                {d.events.length > 0 && ` · ${d.events.join(', ')}`}
              </span>
            </div>
          );
        })}
      </div>
      <div className="relative mt-1 h-4 text-[10px] text-muted" aria-hidden>
        {days.map((d, i) => (i % labelEvery === 0 ? (
          <span key={d.date} className="absolute whitespace-nowrap" style={{ left: `${(i / days.length) * 100}%` }}>{formatDate(d.date).slice(0, 5)}</span>
        ) : null))}
      </div>
      <p className="mt-3 flex flex-wrap items-center gap-4 text-[11px] text-muted">
        <span className="inline-flex items-center gap-1.5"><i className="size-2 rounded-sm bg-chart-2" />vendido</span>
        <span className="inline-flex items-center gap-1.5"><i className="size-2 rounded-sm bg-chart" />previsto</span>
        <span className="inline-flex items-center gap-1.5"><i className="size-1.5 rounded-full bg-warning" />feriado ou data comercial</span>
      </p>
    </div>
  );
}

const WEEKDAY: Record<string, string> = {
  MONDAY: 'Seg', TUESDAY: 'Ter', WEDNESDAY: 'Qua', THURSDAY: 'Qui', FRIDAY: 'Sex', SATURDAY: 'Sáb', SUNDAY: 'Dom',
};

const pct = (factor: number) => {
  const v = Math.round((factor - 1) * 100);
  return v === 0 ? 'igual à média' : `${v > 0 ? '+' : ''}${v}%`;
};

// "Por que esse número?": cada fator em palavras e de onde ele veio.
function Explain({ m }: { m: ForecastModelDto }) {
  const maxW = Math.max(...m.weekday.map((w) => w.factor), 1);
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card title="Por que esse número">
        <div className="flex flex-col gap-4 p-4 text-sm">
          <p>
            <strong>Nível atual:</strong> {formatMoney(m.baselineRevenue)} por dia ({m.baselineSales.toLocaleString('pt-BR')} vendas), a média
            dos últimos 28 dias já sem o efeito de dia da semana, salário e datas.
          </p>
          <div>
            <p className="mb-2"><strong>Dia da semana</strong> <span className="text-muted">(aprendido nas últimas 12 semanas)</span></p>
            <div className="flex h-20 items-end gap-2">
              {m.weekday.map((w) => (
                <div key={w.day} className="flex flex-1 flex-col items-center gap-1">
                  <span className="text-[10px] text-muted tabular">{pct(w.factor)}</span>
                  <span className="block w-full rounded-t-sm bg-chart" style={{ height: `${(w.factor / maxW) * 48}px` }} />
                  <span className="text-[11px] text-muted">{WEEKDAY[w.day]}</span>
                </div>
              ))}
            </div>
          </div>
          <p>
            <strong>Semana do salário</strong> (dias 5 a 9 e 20 a 24):{' '}
            {m.payday.learned ? `${pct(m.payday.factor)} nesses dias.` : 'ainda sem dados suficientes, não entra.'}
          </p>
          <p>
            <strong>Feriado:</strong>{' '}
            {m.holiday.learned ? `vende ${pct(m.holiday.factor)} em relação à média, aprendido com os feriados do histórico.` : 'conta como domingo (o shopping abre em horário de domingo) até haver 2 feriados no histórico.'}
          </p>
          <p>
            <strong>Mês do ano:</strong>{' '}
            {m.monthSeasonality.active ? 'entra na conta, aprendido com o último ano.' : 'ainda não entra: precisa de 1 ano de vendas.'}
          </p>
          {m.accuracy && (
            <p>
              <strong>Acerto:</strong> refazendo a previsão como se fosse 4 semanas atrás, o erro médio por semana foi de{' '}
              {m.accuracy.weeklyErrorPercent.toLocaleString('pt-BR')}%.
            </p>
          )}
        </div>
      </Card>
      <Card title="Datas comerciais">
        <ul className="divide-y divide-border">
          {m.events.map((e) => (
            <li key={e.name} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
              <span>
                {e.name} <span className="text-xs text-muted">· {formatDate(e.date)} · sobe nos {e.rampDays} dias antes</span>
              </span>
              <span className="flex shrink-0 items-center gap-2">
                <span className="tabular font-medium">{pct(e.uplift)}</span>
                <Badge tone={e.learned ? 'success' : 'neutral'}>{e.learned ? 'aprendida' : 'estimativa padrão'}</Badge>
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
