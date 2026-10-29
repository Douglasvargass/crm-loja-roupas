import type { ReactNode } from 'react';
import type { Tone } from '@/data/adapters';
import { cn } from '@/lib/cn';

// Etiqueta de situação (ex.: Confirmada, Sem estoque). A cor vem do "tom", nunca de uma cor fixa: assim a
// paleta da marca (tokens.css) muda num lugar só. O texto sempre acompanha a cor, para quem não distingue cores.
const tones: Record<Tone, string> = {
  neutral: 'bg-surface-2 text-muted',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  error: 'bg-error-soft text-error',
  info: 'bg-info-soft text-info',
  primary: 'bg-primary-soft text-primary',
};

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap', tones[tone])}>
      {children}
    </span>
  );
}
