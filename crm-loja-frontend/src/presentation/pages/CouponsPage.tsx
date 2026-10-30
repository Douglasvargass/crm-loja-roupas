import { useState, type FormEvent } from 'react';
import { useToast } from '@/app/context/ToastContext';
import type { CouponDto, CouponInput, CouponKind } from '@/data/dto';
import { useCouponActions, useCoupons } from '@/data/hooks';
import { formatDate, formatMoney, toIsoDate } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Select } from '../components/atoms/Field';
import { Modal } from '../components/molecules/Modal';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

// Cupons de desconto (RN61 a RN64). O cliente usa no carrinho da loja online; o vendedor, na venda do balcão.
// Nada se apaga: cupom que não vale mais é desligado.
export function CouponsPage() {
  const query = useCoupons();
  const [creating, setCreating] = useState(false);
  return (
    <>
      <PageHeader
        title="Cupons"
        help="Crie códigos de desconto em % ou em R$, com valor mínimo do pedido, validade e limite de usos. O uso só conta quando o pedido é confirmado. Cupom não se apaga: desligue quando não quiser mais."
        subtitle="Códigos de desconto para a loja online e o balcão."
        actions={<Button onClick={() => setCreating(true)}>Novo cupom</Button>}
      />
      <QueryState query={query} isEmpty={(l) => l.length === 0} empty="Nenhum cupom ainda. Crie o primeiro em Novo cupom.">
        {(list) => (
          <Card>
            <DataTable<CouponDto>
              rows={list}
              rowKey={(c) => c.id}
              columns={[
                { header: 'Código', cell: (c) => <span className="font-mono font-semibold">{c.code}</span> },
                { header: 'Desconto', cell: (c) => (c.kind === 'PERCENT' ? `${c.value.toLocaleString('pt-BR')}%` : formatMoney(c.value)) },
                { header: 'Pedido mínimo', cell: (c) => (c.minOrder > 0 ? formatMoney(c.minOrder) : '—') },
                { header: 'Validade', cell: (c) => `${formatDate(c.validFrom)} a ${formatDate(c.validUntil)}` },
                { header: 'Usos', align: 'right', cell: (c) => <span className="tabular">{c.uses}{c.maxUses != null ? ` de ${c.maxUses}` : ''}</span> },
                { header: 'Situação', cell: (c) => <Status c={c} /> },
                { header: '', cell: (c) => <Toggle c={c} /> },
              ]}
            />
          </Card>
        )}
      </QueryState>
      {creating && <NewCoupon onClose={() => setCreating(false)} />}
    </>
  );
}

function Status({ c }: { c: CouponDto }) {
  const today = toIsoDate(new Date());
  if (!c.active) return <Badge tone="neutral">Desligado</Badge>;
  if (today > c.validUntil) return <Badge tone="neutral">Vencido</Badge>;
  if (today < c.validFrom) return <Badge tone="info">Ainda não começou</Badge>;
  if (c.maxUses != null && c.uses >= c.maxUses) return <Badge tone="warning">Esgotado</Badge>;
  return <Badge tone="success">Valendo</Badge>;
}

function Toggle({ c }: { c: CouponDto }) {
  const { setActive } = useCouponActions();
  const toast = useToast();
  return (
    <Button size="sm" variant="ghost" loading={setActive.isPending}
      onClick={async () => {
        try {
          await setActive.mutateAsync({ id: c.id, active: !c.active });
          toast.success(c.active ? `Cupom ${c.code} desligado.` : `Cupom ${c.code} ligado de novo.`);
        } catch (err) {
          toast.error(err);
        }
      }}>
      {c.active ? 'Desligar' : 'Ligar'}
    </Button>
  );
}

function NewCoupon({ onClose }: { onClose: () => void }) {
  const { create } = useCouponActions();
  const toast = useToast();
  const today = toIsoDate(new Date());
  const [f, setF] = useState({ code: '', kind: 'PERCENT' as CouponKind, value: '10', minOrder: '0', validFrom: today, validUntil: '', maxUses: '' });
  const [error, setError] = useState<string>();
  const num = (s: string) => Number(s.replace(',', '.'));

  async function submit(e: FormEvent) {
    e.preventDefault();
    const input: CouponInput = {
      code: f.code.trim(), kind: f.kind, value: num(f.value), minOrder: num(f.minOrder || '0'),
      validFrom: f.validFrom, validUntil: f.validUntil, maxUses: f.maxUses ? Number(f.maxUses) : null,
    };
    if (!/^[A-Za-z0-9_-]{3,30}$/.test(input.code)) return setError('Código: de 3 a 30 letras sem acento, números, - ou _.');
    if (!(input.value > 0)) return setError('O desconto precisa ser maior que zero.');
    if (input.kind === 'PERCENT' && input.value > 90) return setError('O desconto em % vai até 90%.');
    if (!input.validUntil) return setError('Informe até quando o cupom vale.');
    setError(undefined);
    try {
      await create.mutateAsync(input);
      toast.success(`Cupom ${input.code.toUpperCase()} criado.`);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível criar o cupom.');
    }
  }

  return (
    <Modal open title="Novo cupom" onClose={onClose}
      footer={<><Button variant="secondary" onClick={onClose}>Voltar</Button><Button type="submit" form="novo-cupom" loading={create.isPending}>Criar cupom</Button></>}>
      <form id="novo-cupom" onSubmit={submit} className="grid grid-cols-2 gap-3 p-4" noValidate>
        <Input label="Código" className="col-span-2" placeholder="ex.: BLACKFRIDAY" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value.toUpperCase() })} />
        <Select label="Tipo" value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value as CouponKind })}>
          <option value="PERCENT">% do pedido</option>
          <option value="FIXED">Valor em R$</option>
        </Select>
        <Input label={f.kind === 'PERCENT' ? 'Desconto (%)' : 'Desconto (R$)'} inputMode="decimal" value={f.value} onChange={(e) => setF({ ...f, value: e.target.value })} />
        <Input label="Pedido mínimo (R$)" inputMode="decimal" value={f.minOrder} onChange={(e) => setF({ ...f, minOrder: e.target.value })} />
        <Input label="Limite de usos" placeholder="sem limite" inputMode="numeric" value={f.maxUses} onChange={(e) => setF({ ...f, maxUses: e.target.value.replace(/\D/g, '') })} />
        <Input label="Vale de" type="date" value={f.validFrom} onChange={(e) => setF({ ...f, validFrom: e.target.value })} />
        <Input label="Até" type="date" value={f.validUntil} min={f.validFrom} onChange={(e) => setF({ ...f, validUntil: e.target.value })} />
        {error && <p role="alert" className="col-span-2 text-sm text-error">{error}</p>}
      </form>
    </Modal>
  );
}
