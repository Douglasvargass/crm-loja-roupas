import { useState } from 'react';
import { useNavigate } from 'react-router';
import { customerLabel, saleStatusLabel, saleStatusTone } from '@/data/adapters';
import { useSales } from '@/data/hooks';
import type { CustomerDto, SaleStatus, SaleSummaryDto } from '@/data/dto';
import { formatDateTime, formatMoney } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import { Badge } from '../../components/atoms/Badge';
import { Button } from '../../components/atoms/Button';
import { Input, Select } from '../../components/atoms/Field';
import { Icon } from '../../components/atoms/Icon';
import { CustomerPicker } from '../../components/molecules/CustomerPicker';
import { Pagination } from '../../components/molecules/Pagination';
import { QueryState } from '../../components/molecules/QueryState';
import { DataTable } from '../../components/organisms/DataTable';
import { Card, Filters, PageHeader } from '../../components/templates/Page';

export function SalesPage() {
  const navigate = useNavigate();
  const [id, setId] = useState('');
  const [customer, setCustomer] = useState<CustomerDto | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState<SaleStatus | ''>('');
  const [page, setPage] = useState(0);
  const debouncedId = useDebounced(id);

  const query = useSales({
    id: /^\d+$/.test(debouncedId) ? Number(debouncedId) : undefined,
    customerId: customer?.id,
    from: from || undefined,
    to: to || undefined,
    status: status || undefined,
    page,
  });
  const reset = () => setPage(0);

  return (
    <>
      <PageHeader
        title="Vendas"
        subtitle="Consulte por número, cliente, data ou status."
        actions={<Button onClick={() => navigate('/vendas/nova')}><Icon name="plus" className="size-4" />Nova venda</Button>}
      />
      <Card>
        <Filters>
          <Input label="Nº da venda" inputMode="numeric" value={id} onChange={(e) => { setId(e.target.value.replace(/\D/g, '')); reset(); }} />
          {customer ? (
            <div className="flex flex-col gap-1">
              <span className="text-sm font-medium">Cliente</span>
              <div className="flex h-10 items-center justify-between rounded-lg border border-border px-3 text-sm">
                <span className="truncate">{customer.fullName}</span>
                <button type="button" className="text-xs text-primary" onClick={() => { setCustomer(null); reset(); }}>Limpar</button>
              </div>
            </div>
          ) : (
            <CustomerPicker label="Cliente" onPick={(c) => { setCustomer(c); reset(); }} />
          )}
          <div className="grid grid-cols-2 gap-3">
            <Input label="De" type="date" value={from} onChange={(e) => { setFrom(e.target.value); reset(); }} />
            <Input label="Até" type="date" value={to} onChange={(e) => { setTo(e.target.value); reset(); }} />
          </div>
          <Select label="Status" value={status} onChange={(e) => { setStatus(e.target.value as SaleStatus | ''); reset(); }}>
            <option value="">Todos</option>
            {(Object.keys(saleStatusLabel) as SaleStatus[]).map((s) => <option key={s} value={s}>{saleStatusLabel[s]}</option>)}
          </Select>
        </Filters>
        <QueryState query={query} isEmpty={(d) => d.content.length === 0} empty="Nenhuma venda encontrada com esses filtros.">
          {(data) => (
            <>
              <DataTable<SaleSummaryDto>
                rows={data.content}
                rowKey={(s) => s.id}
                onRowClick={(s) => navigate(`/vendas/${s.id}`)}
                columns={[
                  { header: 'Nº', cell: (s) => <span className="tabular font-medium">#{s.id}</span> },
                  { header: 'Data', cell: (s) => formatDateTime(s.confirmedAt ?? s.createdAt) },
                  { header: 'Cliente', cell: (s) => <span className={s.customerName ? '' : 'text-muted'}>{customerLabel(s.customerName)}</span> },
                  { header: 'Itens', cell: (s) => s.itemsQuantity, align: 'right' },
                  { header: 'Total', cell: (s) => <span className="tabular">{formatMoney(s.total)}</span>, align: 'right' },
                  { header: 'Status', cell: (s) => <Badge tone={saleStatusTone[s.status]}>{saleStatusLabel[s.status]}</Badge> },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
            </>
          )}
        </QueryState>
      </Card>
    </>
  );
}
