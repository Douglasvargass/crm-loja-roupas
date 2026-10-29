import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { saleStatusLabel, saleStatusTone } from '@/data/adapters';
import { useCustomer, useCustomerActions, useCustomerSales, useUserActions } from '@/data/hooks';
import { ApiError } from '@/data/http';
import type { CustomerDto, SaleSummaryDto } from '@/data/dto';
import { can } from '@/domain/permissions';
import { formatDateTime, formatMoney, maskCpf, maskPhone, maskZip } from '@/lib/format';
import { Badge } from '../../components/atoms/Badge';
import { Button } from '../../components/atoms/Button';
import { ConfirmDialog } from '../../components/molecules/ConfirmDialog';
import { KpiCard } from '../../components/molecules/KpiCard';
import { QueryState } from '../../components/molecules/QueryState';
import { DataTable } from '../../components/organisms/DataTable';
import { Card, Detail, PageHeader } from '../../components/templates/Page';
import { CustomerFormModal } from './CustomerFormModal';
import { ProvisionalPasswordModal } from '../UsersPage';

export function CustomerDetailPage() {
  const id = Number(useParams().id);
  const query = useCustomer(id);
  return (
    <QueryState query={query}>
      {(d) => <CustomerDetail customer={d.customer} purchases={d.confirmedPurchases} total={d.confirmedTotal} last={d.lastPurchaseAt} userId={d.userId} />}
    </QueryState>
  );
}

function CustomerDetail({ customer: c, purchases, total, last, userId }: { customer: CustomerDto; purchases: number; total: number; last: string | null; userId: number | null }) {
  const { role } = useSession();
  const toast = useToast();
  const navigate = useNavigate();
  const sales = useCustomerSales(c.id);
  const { remove, setActive } = useCustomerActions();
  const { resetPassword } = useUserActions();
  const [reset, setReset] = useState<{ login: string; password: string } | null>(null);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [offerDeactivate, setOfferDeactivate] = useState<string | null>(null);

  async function doDelete() {
    try {
      await remove.mutateAsync(c.id);
      toast.success('Pronto! O cliente foi removido.');
      navigate('/clientes');
    } catch (err) {
      setConfirmDelete(false);
      // HU05: cliente com vendas (ou com login na loja, revisão 29/10) → oferece inativar em vez de excluir.
      if (err instanceof ApiError && (err.code === 'customer_has_sales' || err.code === 'customer_has_login')) setOfferDeactivate(err.message);
      else toast.error(err);
    }
  }

  async function toggleActive(active: boolean) {
    try {
      await setActive.mutateAsync({ id: c.id, active });
      setOfferDeactivate(null);
      toast.success(active ? 'Cliente reativado.' : 'Cliente inativado. Ele continua no histórico de vendas.');
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <>
      <PageHeader
        title={c.fullName} help="Dados do cliente e tudo o que ele já comprou. Cliente com compras não é apagado: dá para inativar."
        subtitle={`Cliente desde ${formatDateTime(c.createdAt)}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => navigate('/clientes')}>Voltar</Button>
            <Button variant="secondary" onClick={() => setEditing(true)}>Editar</Button>
            {userId && role && can.users(role) && (
              <Button variant="secondary" loading={resetPassword.isPending}
                onClick={() => void resetPassword.mutateAsync({ id: userId, module: 'loja' })
                  .then((r) => setReset({ login: r.user.login, password: r.provisionalPassword })).catch((err) => toast.error(err))}>
                Resetar senha da loja
              </Button>
            )}
            {role && can.deleteCustomer(role) && (
              c.active
                ? <Button variant="secondary" onClick={() => setConfirmDelete(true)}>Excluir</Button>
                : <Button variant="secondary" loading={setActive.isPending} onClick={() => void toggleActive(true)}>Reativar</Button>
            )}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Dados" className="lg:col-span-2">
          <dl className="grid grid-cols-1 gap-4 p-4 sm:grid-cols-2">
            <Detail label="CPF"><span className="tabular">{maskCpf(c.cpf)}</span></Detail>
            <Detail label="Situação"><Badge tone={c.active ? 'success' : 'neutral'}>{c.active ? 'Ativo' : 'Inativo'}</Badge></Detail>
            <Detail label="Telefone"><span className="tabular">{maskPhone(c.phone)}</span></Detail>
            <Detail label="E-mail">{c.email}</Detail>
            <Detail label="Endereço">
              {c.street}, {c.number}{c.complement ? ` – ${c.complement}` : ''}<br />
              {c.district} · {c.city}/{c.state} · CEP {maskZip(c.zipCode)}
            </Detail>
          </dl>
        </Card>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-1">
          <KpiCard label="Acesso à loja online" value={userId ? 'Sim' : 'Não'}
            hint={userId ? 'Este cliente entra no site da loja' : 'Só compra no balcão'} />
          <KpiCard label="Compras confirmadas" value={purchases} />
          <KpiCard label="Total comprado" value={formatMoney(total)} hint={last ? `Última: ${formatDateTime(last)}` : 'Ainda sem compras'} />
        </div>
      </div>

      <Card title="Histórico de compras" className="mt-4">
        <QueryState query={sales} isEmpty={(s) => s.length === 0} empty="Este cliente ainda não tem compras.">
          {(rows) => (
            <DataTable<SaleSummaryDto>
              rows={rows}
              rowKey={(s) => s.id}
              onRowClick={(s) => navigate(`/vendas/${s.id}`)}
              columns={[
                { header: 'Nº', cell: (s) => <span className="tabular">#{s.id}</span> },
                { header: 'Data', cell: (s) => formatDateTime(s.confirmedAt ?? s.createdAt) },
                { header: 'Itens', cell: (s) => s.itemsQuantity, align: 'right' },
                { header: 'Total', cell: (s) => <span className="tabular">{formatMoney(s.total)}</span>, align: 'right' },
                { header: 'Status', cell: (s) => <Badge tone={saleStatusTone[s.status]}>{saleStatusLabel[s.status]}</Badge> },
              ]}
            />
          )}
        </QueryState>
      </Card>
      {c.active && (
        <p className="mt-3 text-sm">
          <Link to={`/vendas/nova?cliente=${c.id}`} className="font-medium text-primary hover:underline">Abrir venda para este cliente →</Link>
        </p>
      )}

      {editing && <CustomerFormModal customer={c} onClose={() => setEditing(false)} />}
      {reset && <ProvisionalPasswordModal title="Senha da loja resetada" login={reset.login} password={reset.password} onClose={() => setReset(null)} />}
      <ConfirmDialog
        open={confirmDelete}
        title="Excluir cliente"
        message={<>Excluir <strong>{c.fullName}</strong>? Esta ação não pode ser desfeita.</>}
        confirmLabel="Excluir"
        danger
        loading={remove.isPending}
        onConfirm={() => void doDelete()}
        onClose={() => setConfirmDelete(false)}
      />
      <ConfirmDialog
        open={!!offerDeactivate}
        title="Não é possível excluir"
        message={offerDeactivate}
        confirmLabel="Inativar cliente"
        loading={setActive.isPending}
        onConfirm={() => void toggleActive(false)}
        onClose={() => setOfferDeactivate(null)}
      />
    </>
  );
}
