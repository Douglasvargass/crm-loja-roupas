import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { availabilityLabel, availabilityTone } from '@/data/adapters';
import { useCategories, useProducts } from '@/data/hooks';
import type { AvailabilityFilter, ProductDto } from '@/data/dto';
import { can } from '@/domain/permissions';
import { formatMoney } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import { Badge } from '../../components/atoms/Badge';
import { Button } from '../../components/atoms/Button';
import { Input, Select } from '../../components/atoms/Field';
import { Icon } from '../../components/atoms/Icon';
import { Pagination } from '../../components/molecules/Pagination';
import { QueryState } from '../../components/molecules/QueryState';
import { DataTable } from '../../components/organisms/DataTable';
import { Card, Filters, PageHeader } from '../../components/templates/Page';
import { ProductFormModal } from './ProductFormModal';

export function StockBadge({ p }: { p: ProductDto }) {
  if (p.availability === 'AVAILABLE' && p.lowStock) return <Badge tone="warning">Estoque baixo</Badge>;
  return <Badge tone={availabilityTone[p.availability]}>{availabilityLabel[p.availability]}</Badge>;
}

export function ProductsPage() {
  const { role } = useSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [size, setSize] = useState('');
  const [categoryId, setCategoryId] = useState(params.get('categoria') ?? '');
  const [availability, setAvailability] = useState<AvailabilityFilter>('ALL');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const categories = useCategories();

  const query = useProducts({
    name: useDebounced(name),
    code: useDebounced(code),
    size: useDebounced(size),
    categoryId: categoryId ? Number(categoryId) : undefined,
    availability,
    page,
  });
  const reset = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(0); };

  return (
    <>
      <PageHeader
        title="Produtos"
        subtitle="Catálogo com preço, tamanho, cor e situação de estoque."
        actions={role && can.manageProducts(role) && (
          <Button onClick={() => setCreating(true)}><Icon name="plus" className="size-4" />Novo produto</Button>
        )}
      />
      <Card>
        <Filters>
          <Input label="Nome" placeholder="Ex.: camiseta" value={name} onChange={(e) => reset(setName)(e.target.value)} />
          <Input label="Código" value={code} onChange={(e) => reset(setCode)(e.target.value)} />
          <Select label="Categoria" value={categoryId} onChange={(e) => reset(setCategoryId)(e.target.value)}>
            <option value="">Todas</option>
            {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Tamanho" placeholder="M, 40…" value={size} onChange={(e) => reset(setSize)(e.target.value)} />
            <Select label="Situação" value={availability} onChange={(e) => reset(setAvailability)(e.target.value as AvailabilityFilter)}>
              <option value="ALL">Todas</option>
              <option value="AVAILABLE">Disponíveis</option>
              <option value="LOW_STOCK">Estoque baixo</option>
              <option value="OUT_OF_STOCK">Sem estoque</option>
              <option value="INACTIVE">Inativos</option>
            </Select>
          </div>
        </Filters>
        <QueryState query={query} isEmpty={(d) => d.content.length === 0} empty="Nenhum produto encontrado com esses filtros.">
          {(data) => (
            <>
              <DataTable<ProductDto>
                rows={data.content}
                rowKey={(p) => p.id}
                onRowClick={(p) => navigate(`/produtos/${p.id}`)}
                columns={[
                  { header: 'Código', cell: (p) => <span className="font-mono text-xs">{p.code}</span> },
                  { header: 'Nome', cell: (p) => <span className="font-medium">{p.name}</span> },
                  { header: 'Categoria', cell: (p) => p.categoryName },
                  { header: 'Tam.', cell: (p) => p.size, align: 'center' },
                  { header: 'Cor', cell: (p) => p.color },
                  { header: 'Preço', cell: (p) => <span className="tabular">{formatMoney(p.price)}</span>, align: 'right' },
                  { header: 'Estoque', cell: (p) => <span className="tabular">{p.stockQty}</span>, align: 'right' },
                  { header: 'Situação', cell: (p) => <StockBadge p={p} /> },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      {creating && <ProductFormModal onClose={() => setCreating(false)} />}
    </>
  );
}
