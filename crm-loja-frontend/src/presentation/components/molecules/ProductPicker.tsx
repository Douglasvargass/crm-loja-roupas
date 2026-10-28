import { useState } from 'react';
import { useProducts } from '@/data/hooks';
import type { ProductDto } from '@/data/dto';
import { formatMoney } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import { Input } from '../atoms/Field';

interface Props {
  label: string;
  onPick: (p: ProductDto) => void;
  onlyAvailable?: boolean;
  placeholder?: string;
}

// Busca de produto por nome ou código. Na venda, só aparecem ativos com estoque (RN22).
export function ProductPicker({ label, onPick, onlyAvailable, placeholder = 'Digite nome ou código…' }: Props) {
  const [term, setTerm] = useState('');
  const debounced = useDebounced(term.trim());
  const looksLikeCode = /\d/.test(debounced) && /-/.test(debounced);
  const query = useProducts(
    {
      name: looksLikeCode ? undefined : debounced,
      code: looksLikeCode ? debounced : undefined,
      availability: onlyAvailable ? 'AVAILABLE' : 'ALL',
      page: 0,
      pageSize: 8,
    },
    debounced.length >= 2,
  );

  return (
    <div className="relative">
      <Input label={label} placeholder={placeholder} value={term} onChange={(e) => setTerm(e.target.value)} autoComplete="off" />
      {debounced.length >= 2 && (
        <ul className="absolute z-20 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-border bg-surface shadow-lg" role="listbox">
          {query.isPending && <li className="px-3 py-2 text-sm text-muted">Buscando…</li>}
          {query.data?.content.length === 0 && (
            <li className="px-3 py-2 text-sm text-muted">{onlyAvailable ? 'Nenhum produto disponível encontrado.' : 'Nenhum produto encontrado.'}</li>
          )}
          {query.data?.content.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                role="option"
                aria-selected={false}
                onClick={() => {
                  onPick(p);
                  setTerm('');
                }}
                className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-surface-2"
              >
                <span className="min-w-0">
                  <span className="block truncate font-medium">{p.name}</span>
                  <span className="text-xs text-muted">{p.code} · {p.size} · {p.color}</span>
                </span>
                <span className="shrink-0 text-right">
                  <span className="tabular block">{formatMoney(p.price)}</span>
                  <span className="text-xs text-muted">{p.stockQty} em estoque</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
