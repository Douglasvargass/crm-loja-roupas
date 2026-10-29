import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface Column<T> {
  header: string;
  cell: (row: T) => ReactNode;
  align?: 'left' | 'right' | 'center';
  className?: string;
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  onRowClick?: (row: T) => void;
}

// Computador: tabela. Celular (< md): cada linha vira um cartão (a primeira coluna é o título e as demais
// aparecem como "nome: valor"), então não precisa rolar para o lado.
export function DataTable<T>({ columns, rows, rowKey, onRowClick }: Props<T>) {
  const align = (a?: Column<T>['align']) => (a === 'right' ? 'text-right' : a === 'center' ? 'text-center' : 'text-left');
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface-2/60">
              {columns.map((c) => (
                <th key={c.header} scope="col" className={cn('px-4 py-2.5 font-medium text-muted', align(c.align), c.className)}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={cn('border-b border-border last:border-0', onRowClick && 'cursor-pointer hover:bg-surface-2/60')}
              >
                {columns.map((c) => (
                  <td key={c.header} className={cn('px-4 py-2.5', align(c.align), c.className)}>
                    {c.cell(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul className="divide-y divide-border md:hidden">
        {rows.map((row) => (
          <li
            key={rowKey(row)}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            className={cn('flex flex-col gap-1.5 px-4 py-3', onRowClick && 'cursor-pointer active:bg-surface-2')}
          >
            {columns.map((c, i) =>
              i === 0 ? (
                <div key={c.header} className="text-sm font-medium">{c.cell(row)}</div>
              ) : (
                <div key={c.header} className="flex items-start justify-between gap-3 text-sm">
                  <span className="shrink-0 text-muted">{c.header}</span>
                  <span className="min-w-0 text-right">{c.cell(row)}</span>
                </div>
              ),
            )}
          </li>
        ))}
      </ul>
    </>
  );
}
