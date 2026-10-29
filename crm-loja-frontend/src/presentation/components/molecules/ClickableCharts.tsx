import { useMemo } from 'react';
import { formatDate, formatMoney } from '@/lib/format';
import { cn } from '@/lib/cn';

// Gráficos do dashboard. Cada barra é um botão: clicar seleciona (e o painel inteiro se filtra por aquilo),
// clicar de novo na mesma barra remove o filtro. Quando há seleção, as outras barras ficam apagadas.

// Escala de cor: t de 0 a 1 vai de bordô (baixo) a amarelo (meio) até azul (alto), sempre em tons suaves.
function scale(t: number): string {
  const x = Math.min(Math.max(t, 0), 1);
  return x < 0.5
    ? `color-mix(in oklab, var(--g1), var(--g2) ${Math.round(x * 200)}%)`
    : `color-mix(in oklab, var(--g2), var(--g3) ${Math.round((x - 0.5) * 200)}%)`;
}
const GRADIENT = 'linear-gradient(90deg, var(--g1), var(--g2), var(--g3))';

export interface DayPoint { date: string; sales: number; revenue: number }

export function DayBars({ data, selected, onPick }: { data: DayPoint[]; selected?: string | null; onPick: (date: string) => void }) {
  const max = useMemo(() => Math.max(...data.map((x) => x.revenue), 0), [data]);
  if (max === 0) return <p className="p-6 text-center text-sm text-muted">Sem faturamento no período.</p>;
  const showEvery = Math.ceil(data.length / 8);
  // Degradê suave do menor para o maior valor: bordô, passando por amarelo, até azul.
  const min = Math.min(...data.map((x) => x.revenue));
  const light = (v: number) => scale(max > min ? (v - min) / (max - min) : 0.5);
  return (
    <div className="px-3 pt-4 pb-2 sm:px-4">
      <div className="flex h-44 items-end gap-[2px]" role="group" aria-label="Faturamento por dia. Clique numa barra para filtrar o dia.">
        {data.map((x, i) => {
          const isSel = selected === x.date;
          const dim = !!selected && !isSel;
          const tip = i < data.length / 3 ? 'left-0' : i > (data.length * 2) / 3 ? 'right-0' : 'left-1/2 -translate-x-1/2';
          return (
            <button
              key={x.date}
              type="button"
              aria-pressed={isSel}
              aria-label={`${formatDate(x.date)}: ${formatMoney(x.revenue)}, ${x.sales} venda(s)`}
              onClick={() => onPick(x.date)}
              className={cn(
                'group relative flex h-full min-w-0 flex-1 items-end rounded-sm hover:bg-primary-soft focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:outline-none',
                isSel && 'bg-primary-soft',
              )}
            >
              <span
                className={cn('block w-full rounded-t-sm transition-opacity', dim ? 'opacity-30' : 'group-hover:opacity-80')}
                style={{ height: `${Math.max((x.revenue / max) * 100, x.revenue > 0 ? 2 : 0)}%`, background: light(x.revenue) }}
              />
              <span className={cn('pointer-events-none absolute bottom-full z-10 mb-1 hidden rounded-md border border-border bg-surface px-2 py-1 text-xs whitespace-nowrap shadow group-hover:block group-focus-visible:block', tip)}>
                {formatDate(x.date)}: {formatMoney(x.revenue)} · {x.sales} venda(s)
              </span>
            </button>
          );
        })}
      </div>
      <p className="mt-3 flex items-center gap-2 text-[11px] text-muted" aria-hidden>
        menor <i className="h-1.5 w-20 rounded-full" style={{ background: GRADIENT }} /> maior
      </p>
      <div className="mt-1 flex gap-[2px] text-[10px] text-muted" aria-hidden>
        {data.map((x, i) => (
          <div key={x.date} className="min-w-0 flex-1 truncate text-center">
            {i % showEvery === 0 ? formatDate(x.date).slice(0, 5) : ''}
          </div>
        ))}
      </div>
    </div>
  );
}

export interface BarRow { id: number; label: string; sub: string; value: number; display: string }

export function ClickBars({ rows, selectedId, onPick, empty }: { rows: BarRow[]; selectedId?: number | null; onPick: (id: number) => void; empty: string }) {
  if (rows.length === 0) return <p className="p-6 text-center text-sm text-muted">{empty}</p>;
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <ul className="flex flex-col gap-1 p-2 sm:p-3">
      {rows.map((r) => {
        const isSel = selectedId === r.id;
        const dim = selectedId != null && !isSel;
        return (
          <li key={r.id}>
            <button
              type="button"
              aria-pressed={isSel}
              onClick={() => onPick(r.id)}
              className={cn(
                'w-full rounded-lg px-2.5 py-2 text-left transition-colors focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:outline-none',
                isSel ? 'bg-primary-soft ring-1 ring-primary' : 'hover:bg-surface-2',
                dim && 'opacity-45',
              )}
            >
              <span className="mb-1 flex justify-between gap-2 text-sm">
                <span className="truncate">
                  {r.label} <span className="text-xs text-muted">{r.sub}</span>
                </span>
                <span className="tabular shrink-0 font-medium">{r.display}</span>
              </span>
              <span className="block h-2 rounded-full bg-surface-2">
<span className="block h-2 rounded-full bg-chart" style={{ width: `${(r.value / max) * 100}%` }} />
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
