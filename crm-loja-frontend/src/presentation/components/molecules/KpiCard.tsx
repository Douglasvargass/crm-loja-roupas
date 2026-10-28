import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface Props {
  label: string;
  value: ReactNode;
  // Variação contra o período anterior, em %. null = sem base de comparação.
  change?: number | null;
  hint?: string;
  tone?: 'default' | 'warning' | 'error';
}

export function KpiCard({ label, value, change, hint, tone = 'default' }: Props) {
  return (
    <div className="flex flex-col gap-1 rounded-xl border border-border bg-surface p-4">
      <span className="text-sm text-muted">{label}</span>
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
    </div>
  );
}
