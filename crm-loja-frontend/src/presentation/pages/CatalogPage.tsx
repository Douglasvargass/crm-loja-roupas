import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import type { ProductDto } from '@/data/dto';
import { useCategories, useProducts } from '@/data/hooks';
import { can } from '@/domain/permissions';
import { formatMoney } from '@/lib/format';
import { Icon } from '../components/atoms/Icon';
import { ProductPhoto } from '../components/atoms/ProductPhoto';
import { Skeleton } from '../components/atoms/Skeleton';
import { Input, Select } from '../components/atoms/Field';
import { QueryState } from '../components/molecules/QueryState';
import { ProductGallery } from '../components/organisms/ProductGallery';
import { PageHeader } from '../components/templates/Page';
import { StockBadge } from './products/ProductsPage';

// Catálogo visual: todas as peças ativas com a foto de capa. Clicar abre a galeria (frente, costas, lateral e
// detalhe), com "Ampliar" em tela cheia.
export function CatalogPage() {
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [open, setOpen] = useState<ProductDto | null>(null);
  const categories = useCategories();
  const query = useProducts({ name: name.trim() || undefined, categoryId, availability: 'ALL', page: 0, pageSize: 60 });

  return (
    <>
      <PageHeader
        title="Catálogo"
        help="Todas as peças com as fotos. Clique numa peça para ver a galeria (frente, costas, lateral e detalhe) e use Ampliar para ver em tela cheia."
        subtitle="As peças da loja com as fotos de cada uma."
      />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Input label="Buscar" placeholder="Nome da peça" className="w-64" value={name} onChange={(e) => setName(e.target.value)} />
        <Select label="Categoria" className="w-48" value={categoryId ?? ''} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Todas</option>
          {(categories.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
      </div>
      <QueryState query={query} isEmpty={(p) => p.content.length === 0} empty="Nenhuma peça com esse filtro."
        skeleton={<div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">{Array.from({ length: 8 }, (_, i) => <Skeleton key={i} className="aspect-[3/4]" />)}</div>}>
        {(page) => (
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {page.content.filter((p) => p.active).map((p) => (
              <li key={p.id}>
                <button type="button" onClick={() => setOpen(p)}
                  className="group flex w-full flex-col overflow-hidden rounded-xl border border-border bg-surface text-left transition-shadow hover:shadow-md focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:outline-none">
                  <span className="overflow-hidden">
                    <ProductPhoto productId={p.id} alt={p.name} className="aspect-square w-full transition-transform duration-300 group-hover:scale-105" />
                  </span>
                  <span className="flex flex-col gap-1 p-3">
                    <span className="line-clamp-1 text-sm font-medium">{p.name}</span>
                    <span className="text-xs text-muted">{p.size} · {p.color}</span>
                    <span className="mt-1 flex items-center justify-between gap-2">
                      <span className="tabular font-semibold">{formatMoney(p.price)}</span>
                      <StockBadge p={p} />
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
      {open && <QuickView p={open} onClose={() => setOpen(null)} />}
    </>
  );
}

// Painel da peça: galeria + dados. Não usa <dialog> para a tela cheia da galeria poder ficar por cima.
function QuickView({ p, onClose }: { p: ProductDto; onClose: () => void }) {
  const { role } = useSession();
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !document.querySelector('[aria-label^="Fotos de"]')) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center sm:p-6" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label={p.name} onClick={(e) => e.stopPropagation()}
        className="max-h-[92dvh] w-full overflow-y-auto rounded-t-2xl bg-surface shadow-2xl sm:max-w-4xl sm:rounded-2xl">
        <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
          <h2 className="truncate text-base font-semibold">{p.name}</h2>
          <button type="button" onClick={onClose} aria-label="Fechar" className="rounded-lg p-2 hover:bg-surface-2"><Icon name="close" className="size-5" /></button>
        </div>
        <div className="grid gap-6 p-5 md:grid-cols-[1.2fr_1fr]">
          <ProductGallery productId={p.id} name={p.name} editable={!!role && can.manageProducts(role)} />
          <div className="flex flex-col gap-3 text-sm">
            <span className="tabular text-2xl font-semibold">{formatMoney(p.price)}</span>
            <StockBadge p={p} />
            <dl className="grid grid-cols-2 gap-3">
              <div><dt className="text-xs text-muted">Código</dt><dd>{p.code}</dd></div>
              <div><dt className="text-xs text-muted">Categoria</dt><dd>{p.categoryName}</dd></div>
              <div><dt className="text-xs text-muted">Tamanho</dt><dd>{p.size}</dd></div>
              <div><dt className="text-xs text-muted">Cor</dt><dd>{p.color}</dd></div>
              <div><dt className="text-xs text-muted">Em estoque</dt><dd className="tabular">{p.stockQty}</dd></div>
            </dl>
            {p.description && <p className="text-muted">{p.description}</p>}
            <Link to={`/produtos/${p.id}`} className="mt-auto text-sm font-medium text-primary hover:underline">Abrir a ficha completa ›</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
