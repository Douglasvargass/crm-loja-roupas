import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { requestStatusLabel, requestStatusTone } from '@/data/adapters';
import { useRequestActions, useRequestAlerts } from '@/data/hooks';
import { cn } from '@/lib/cn';
import { formatDateTime } from '@/lib/format';
import { Badge } from '../atoms/Badge';
import { Icon } from '../atoms/Icon';

// Spec crm/09: o sino do topo. Estoque e gerente veem os chamados abertos; o vendedor vê os seus que fecharam.
// Ao abrir o sino, as novidades do vendedor contam como vistas e o número zera.
export function AlertBell() {
  const alerts = useRequestAlerts();
  const { seen } = useRequestActions();
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const a = alerts.data;
  const count = a?.count ?? 0;

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => { if (box.current && !box.current.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next && a?.kind === 'MY_UPDATES' && count > 0) seen.mutate();
  }

  const title = a?.kind === 'MY_UPDATES' ? 'Novidades dos seus chamados' : 'Chamados abertos';
  return (
    <div ref={box} className="relative">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-label={count > 0 ? `${title}: ${count}` : title}
        className="relative flex size-9 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-text"
      >
        <Icon name="alert" className="size-5" />
        {count > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-error px-1 text-[11px] font-semibold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-1rem)] rounded-xl border border-border bg-surface shadow-xl">
          <div className="border-b border-border px-4 py-2.5 text-sm font-semibold">{title}</div>
          {!a || a.items.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-muted">
              {a?.kind === 'MY_UPDATES' ? 'Nenhuma novidade nos seus chamados.' : 'Nenhum chamado aberto. Tudo em ordem!'}
            </p>
          ) : (
            <ul className="max-h-80 divide-y divide-border overflow-y-auto">
              {a.items.map((r) => (
                <li key={r.id} className="px-4 py-2.5 text-sm">
                  <div className="flex items-start justify-between gap-2">
                    <span className="min-w-0 font-medium">{r.description}</span>
                    <Badge tone={requestStatusTone[r.status]}>{requestStatusLabel[r.status]}</Badge>
                  </div>
                  <p className={cn('mt-0.5 text-xs text-muted')}>
                    {r.status === 'OPEN'
                      ? `${r.quantity} un. · aberto por ${r.openedByName ?? '—'} em ${formatDateTime(r.createdAt)}`
                      : r.closingNote}
                  </p>
                </li>
              ))}
            </ul>
          )}
          <Link to="/chamados" onClick={() => setOpen(false)} className="block border-t border-border px-4 py-2.5 text-center text-sm font-medium text-primary hover:bg-surface-2">
            Ver todos os chamados
          </Link>
        </div>
      )}
    </div>
  );
}
