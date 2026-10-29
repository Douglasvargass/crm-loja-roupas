import { useSession } from '@/app/context/SessionContext';
import { apiUrl } from '@/data/http';
import { can } from '@/domain/permissions';
import { Icon } from '../atoms/Icon';

type Query = Record<string, string | number | boolean | null | undefined>;

// HU32: baixa a lista em planilha (CSV) com os mesmos filtros da tela. Só gerente; o cookie vai junto no link.
export function ExportButton({ path, query }: { path: string; query?: Query }) {
  const { role, me } = useSession();
  if (!role || !can.export(role) || me?.viewAs) return null;
  return (
    <a
      href={apiUrl(path, query)}
      download
      title="Baixa uma planilha com o que está filtrado nesta tela"
      className="inline-flex h-10 items-center gap-2 rounded-lg border border-border bg-surface px-4 text-sm font-medium text-text hover:bg-surface-2"
    >
      <Icon name="send" className="size-4 rotate-90" />
      Exportar planilha
    </a>
  );
}
