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

// Tabela com rolagem horizontal própria: no celular a página não rola de lado.
export function DataTable<T>({ columns, rows, rowKey, onRowClick }: Props<T>) {
  const align = (a?: Column<T>['align']) => (a === 'right' ? 'text-right' : a === 'center' ? 'text-center' : 'text-left');
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
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
  );
}
