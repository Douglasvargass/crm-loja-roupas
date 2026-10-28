import { useState } from 'react';
import { useCustomers } from '@/data/hooks';
import type { CustomerDto } from '@/data/dto';
import { maskCpf } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import { Input } from '../atoms/Field';

// Busca de cliente ativo por nome ou CPF (inativo não abre venda nova).
export function CustomerPicker({ label, onPick }: { label: string; onPick: (c: CustomerDto) => void }) {
  const [term, setTerm] = useState('');
  const debounced = useDebounced(term.trim());
  const query = useCustomers({ search: debounced, active: true, page: 0, size: 8 });
  const show = debounced.length >= 2;

  return (
    <div className="relative">
      <Input label={label} placeholder="Nome ou CPF…" value={term} onChange={(e) => setTerm(e.target.value)} autoComplete="off" />
      {show && (
        <ul className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-border bg-surface shadow-lg" role="listbox">
          {query.isFetching && !query.data && <li className="px-3 py-2 text-sm text-muted">Buscando…</li>}
          {query.data?.content.length === 0 && <li className="px-3 py-2 text-sm text-muted">Nenhum cliente encontrado.</li>}
          {query.data?.content.map((c) => (
            <li key={c.id}>
              <button
                type="button"
                role="option"
                aria-selected={false}
                className="flex w-full justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-surface-2"
                onClick={() => {
                  onPick(c);
                  setTerm('');
                }}
              >
                <span className="truncate font-medium">{c.fullName}</span>
                <span className="tabular shrink-0 text-xs text-muted">{maskCpf(c.cpf)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
