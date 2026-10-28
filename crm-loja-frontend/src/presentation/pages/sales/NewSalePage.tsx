import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useToast } from '@/app/context/ToastContext';
import { useCustomer, useSaleActions } from '@/data/hooks';
import type { CustomerDto } from '@/data/dto';
import { maskCpf } from '@/lib/format';
import { Button } from '../../components/atoms/Button';
import { CustomerPicker } from '../../components/molecules/CustomerPicker';
import { Card, PageHeader } from '../../components/templates/Page';

// HU21: abre a venda (Aberta, total zero). O cliente é opcional.
export function NewSalePage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [params] = useSearchParams();
  const preselected = Number(params.get('cliente') ?? 0);
  const fromUrl = useCustomer(preselected);
  const [picked, setPicked] = useState<CustomerDto | null | undefined>(undefined);
  const customer = picked === undefined ? (preselected ? fromUrl.data?.customer ?? null : null) : picked;
  const { open } = useSaleActions();

  async function start(customerId: number | null) {
    try {
      const sale = await open.mutateAsync(customerId);
      navigate(`/vendas/${sale.id}`, { replace: true });
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <>
      <PageHeader title="Nova venda" subtitle="Escolha o cliente, se ele quiser se identificar." />
      <Card className="max-w-xl">
        <div className="flex flex-col gap-4 p-4">
          {customer ? (
            <div className="flex items-center justify-between rounded-lg border border-border bg-surface-2 px-3 py-2">
              <div>
                <div className="text-sm font-medium">{customer.fullName}</div>
                <div className="tabular text-xs text-muted">{maskCpf(customer.cpf)}</div>
              </div>
              <button type="button" className="text-xs text-primary" onClick={() => setPicked(null)}>Trocar</button>
            </div>
          ) : (
            <CustomerPicker label="Cliente (opcional)" onPick={setPicked} />
          )}
          <div className="flex flex-wrap gap-2">
            <Button loading={open.isPending} onClick={() => void start(customer?.id ?? null)}>
              {customer ? 'Abrir venda para este cliente' : 'Abrir venda sem cliente'}
            </Button>
            <Button variant="secondary" onClick={() => navigate('/vendas')}>Voltar</Button>
          </div>
          {!customer && <p className="text-xs text-muted">Sem cliente, a venda aparece como “Consumidor não identificado”.</p>}
        </div>
      </Card>
    </>
  );
}
