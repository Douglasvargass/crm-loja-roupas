import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useCustomers } from '@/data/hooks';
import type { CustomerDto } from '@/data/dto';
import { useDebounced } from '@/lib/useDebounced';
import { maskCpf, maskPhone } from '@/lib/format';
import { Badge } from '../../components/atoms/Badge';
import { Button } from '../../components/atoms/Button';
import { Input, Select } from '../../components/atoms/Field';
import { Icon } from '../../components/atoms/Icon';
import { Pagination } from '../../components/molecules/Pagination';
import { QueryState } from '../../components/molecules/QueryState';
import { DataTable } from '../../components/organisms/DataTable';
import { Card, Filters, PageHeader } from '../../components/templates/Page';
import { ExportButton } from '../../components/molecules/ExportButton';
import { CustomerFormModal } from './CustomerFormModal';

// HU01 e HU02: lista de clientes com busca por nome ou CPF e filtro de ativos. Clicar na linha abre a ficha
// (HU03 e HU06, com o histórico de compras). Começa mostrando só os ativos, que é o que o balcão usa.
export function CustomersPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [active, setActive] = useState<'all' | 'true' | 'false'>('true');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const debounced = useDebounced(search);

  const query = useCustomers({ search: debounced, active: active === 'all' ? undefined : active === 'true', page });

  return (
    <>
      <PageHeader
        title="Clientes" help="Cadastre e encontre clientes por nome ou CPF (com ou sem pontos). O CEP preenche o endereço."
        subtitle="Cadastro, busca e histórico de compras."
        actions={<>
          <ExportButton path="/crm/export/customers.csv" query={{ search: debounced, active: active === 'all' ? undefined : active === 'true' }} />
          <Button onClick={() => setCreating(true)}><Icon name="plus" className="size-4" />Novo cliente</Button>
        </>}
      />
      <Card>
        <Filters>
          <Input className="sm:col-span-2" label="Buscar por nome ou CPF" placeholder="Ex.: joao ou 529.982" value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <Select label="Situação" value={active} onChange={(e) => { setActive(e.target.value as typeof active); setPage(0); }}>
            <option value="true">Ativos</option>
            <option value="false">Inativos</option>
            <option value="all">Todos</option>
          </Select>
        </Filters>
        <QueryState query={query} isEmpty={(d) => d.content.length === 0} empty="Nenhum cliente encontrado.">
          {(data) => (
            <>
              <DataTable<CustomerDto>
                rows={data.content}
                rowKey={(c) => c.id}
                onRowClick={(c) => navigate(`/clientes/${c.id}`)}
                columns={[
                  { header: 'Nome', cell: (c) => <span className="font-medium">{c.fullName}</span> },
                  { header: 'CPF', cell: (c) => <span className="tabular">{maskCpf(c.cpf)}</span> },
                  { header: 'Telefone', cell: (c) => <span className="tabular">{maskPhone(c.phone)}</span> },
                  { header: 'Cidade', cell: (c) => `${c.city}/${c.state}` },
                  { header: 'Situação', cell: (c) => <Badge tone={c.active ? 'success' : 'neutral'}>{c.active ? 'Ativo' : 'Inativo'}</Badge> },
                ]}
              />
              <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      {creating && <CustomerFormModal onClose={() => setCreating(false)} />}
    </>
  );
}
