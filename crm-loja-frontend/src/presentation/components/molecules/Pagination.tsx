import { Button } from '../atoms/Button';

interface Props {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, onChange }: Props) {
  if (totalPages <= 1) {
    return <p className="px-4 py-3 text-xs text-muted">{totalElements} registro(s)</p>;
  }
  return (
    <div className="flex items-center justify-between gap-2 px-4 py-3 text-sm">
      <span className="text-xs text-muted">
        Página {page + 1} de {totalPages} · {totalElements} registro(s)
      </span>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Anterior
        </Button>
        <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Próxima
        </Button>
      </div>
    </div>
  );
}
