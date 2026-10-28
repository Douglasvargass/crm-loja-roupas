import type { ReactNode } from 'react';
import type { UseQueryResult } from '@tanstack/react-query';
import { Button } from '../atoms/Button';
import { Skeleton } from '../atoms/Skeleton';

interface Props<T> {
  query: UseQueryResult<T>;
  isEmpty?: (data: T) => boolean;
  empty?: string;
  skeleton?: ReactNode;
  children: (data: T) => ReactNode;
}

// Toda seção com dados passa por aqui: esqueleto ao carregar, erro com "Tentar de novo", vazio explicado.
export function QueryState<T>({ query, isEmpty, empty = 'Nada por aqui ainda.', skeleton, children }: Props<T>) {
  if (query.isPending) {
    return (
      <>
        {skeleton ?? (
          <div className="space-y-2 p-4">
            <Skeleton className="h-5 w-1/3" />
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-5/6" />
          </div>
        )}
      </>
    );
  }
  if (query.isError) {
    return (
      <div role="alert" className="flex flex-col items-center gap-3 p-8 text-center">
        <p className="text-sm text-error">{query.error.message}</p>
        <Button variant="secondary" size="sm" onClick={() => void query.refetch()}>
          Tentar de novo
        </Button>
      </div>
    );
  }
  if (isEmpty?.(query.data)) return <p className="p-8 text-center text-sm text-muted">{empty}</p>;
  return <>{children(query.data)}</>;
}
