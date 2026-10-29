import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { cn } from '@/lib/cn';

interface Props {
  label: string;
  value: ReactNode;
  // Variação contra o período anterior, em %. null = sem base de comparação.
  change?: number | null;
  hint?: string;
  tone?: 'default' | 'warning' | 'error';
  // Com `to`, o cartão inteiro é um link para a tela correspondente.
  to?: string;
}

export function KpiCard({ label, value, change, hint, tone = 'default', to }: Props) {
  const body = (
    <>
      <span className="flex items-center justify-between gap-2 text-sm text-muted">
        {label}
        {to && <span aria-hidden className="text-base leading-none text-muted/70 transition-transform group-hover:translate-x-0.5">›</span>}
      </span>
      <span
        className={cn(
          'tabular text-2xl font-semibold tracking-tight',
          tone === 'warning' && 'text-warning',
          tone === 'error' && 'text-error',
        )}
      >
        {value}
      </span>
      {change !== undefined && (
        <span className="text-xs text-muted">
          {change === null ? (
            'sem vendas no período anterior'
          ) : (
            <>
              <span className={cn('font-semibold', change >= 0 ? 'text-success' : 'text-error')}>
                {change >= 0 ? '▲' : '▼'} {Math.abs(change).toFixed(0)}%
              </span>{' '}
              vs. período anterior
            </>
          )}
        </span>
      )}
      {hint && <span className="text-xs text-muted">{hint}</span>}
    </>
  );
  const base = 'flex flex-col gap-1 rounded-xl border border-border bg-surface p-4';
  if (!to) return <div className={base}>{body}</div>;
  return (
    <Link to={to} className={cn(base, 'group transition-colors hover:border-primary hover:bg-primary-soft/40 focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:outline-none')}>
      {body}
    </Link>
  );
}
