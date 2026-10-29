import { useState } from 'react';
import { useToast } from '@/app/context/ToastContext';
import { targetStatusLabel, targetStatusTone } from '@/data/adapters';
import type { TargetBoardDto, TargetBoardRowDto } from '@/data/dto';
import { useSetTarget, useTargets } from '@/data/hooks';
import { cn } from '@/lib/cn';
import { formatMoney, toIsoDate } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input } from '../components/atoms/Field';
import { Skeleton } from '../components/atoms/Skeleton';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

const currentMonth = () => toIsoDate(new Date()).slice(0, 7);

function monthPlus(month: string, n: number) {
  const [y = 0, m = 1] = month.split('-').map(Number);
  const d = new Date(y, m - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

// Spec crm/06: o gerente define a meta de cada vendedor e acompanha a equipe. Ordem alfabética, sem ranking.
export function TargetsPage() {
  const [month, setMonth] = useState(currentMonth);
  const query = useTargets(month);
  return (
    <>
      <PageHeader
        title="Metas" help="Defina a meta de cada vendedor por mês. Mês que já passou não muda, e cada mudança fica registrada."
        subtitle="A meta de vendas de cada vendedor no mês e quanto cada um já vendeu."
        actions={
          <Input
            label="Mês"
            type="month"
            className="w-44"
            value={month}
            max={monthPlus(currentMonth(), 12)}
            onChange={(e) => e.target.value && setMonth(e.target.value)}
          />
        }
      />
      <QueryState query={query} skeleton={<Skeleton className="h-64" />}>
        {(b) => <Board b={b} />}
      </QueryState>
    </>
  );
}

function Board({ b }: { b: TargetBoardDto }) {
  const pct = b.totalTarget > 0 ? Math.round((b.totalRevenue / b.totalTarget) * 1000) / 10 : null;
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted">
        Meta da equipe <strong className="text-text">{formatMoney(b.totalTarget)}</strong> · vendido{' '}
        <strong className="text-text">{formatMoney(b.totalRevenue)}</strong>
        {pct != null && <> ({pct.toLocaleString('pt-BR')}%)</>}
        {' · '}
        {b.closed ? 'mês fechado' : `${b.businessDaysLeft} de ${b.businessDaysInMonth} dias úteis pela frente`}
      </p>
      {!b.editable && (
        <p className="rounded-lg border border-warning/30 bg-warning-soft px-3 py-2 text-sm text-warning">
          Este mês já fechou: as metas dele ficam como estão, para o histórico não mudar.
        </p>
      )}
      <Card>
        <BoardTable rows={b.rows} month={b.month} editable={b.editable} />
      </Card>
      <p className="text-xs text-muted">
        Conta a venda confirmada no mês por quem abriu a venda. "No ritmo": mantendo a média por dia útil (segunda a
        sábado), o vendedor alcança a meta. "Atenção": chega entre 80% e 100%. Cada mudança de meta fica registrada na
        auditoria.
      </p>
    </div>
  );
}

function BoardTable({ rows, month, editable }: { rows: TargetBoardRowDto[]; month: string; editable: boolean }) {
  if (rows.length === 0) {
    return <p className="p-8 text-center text-sm text-muted">Nenhum vendedor ativo. Crie a conta em Usuários com o papel Vendedor(a).</p>;
  }
  return (
    <DataTable<TargetBoardRowDto>
      rows={rows}
      rowKey={(r) => r.sellerId}
      columns={[
        { header: 'Vendedor(a)', cell: (r) => <span className="font-medium">{r.sellerName}</span> },
        {
          header: 'Meta',
          cell: (r) => (editable ? <TargetInput row={r} month={month} /> : r.target == null ? <span className="text-muted">sem meta</span> : <span className="tabular">{formatMoney(r.target)}</span>),
        },
        { header: 'Vendido', align: 'right', cell: (r) => <span className="tabular">{formatMoney(r.revenue)}</span> },
        { header: '% da meta', cell: (r) => <Progress percent={r.percent} /> },
        { header: 'Projeção', align: 'right', cell: (r) => <span className="tabular text-muted">{formatMoney(r.projection)}</span> },
        { header: 'Falta', align: 'right', cell: (r) => <span className="tabular">{r.remaining == null ? '—' : formatMoney(r.remaining)}</span> },
        { header: 'Situação', cell: (r) => <Badge tone={targetStatusTone[r.status]}>{targetStatusLabel[r.status]}</Badge> },
      ]}
    />
  );
}

function Progress({ percent }: { percent: number | null }) {
  if (percent == null) return <span className="text-muted">—</span>;
  return (
    <span className="flex items-center gap-2">
      <span className="h-2 w-20 overflow-hidden rounded-full bg-surface-2" aria-hidden>
        <span className={cn('block h-full rounded-full', percent >= 100 ? 'bg-success' : 'bg-primary')} style={{ width: `${Math.min(100, percent)}%` }} />
      </span>
      <span className="tabular text-sm">{percent.toLocaleString('pt-BR')}%</span>
    </span>
  );
}

// Campo da meta. O botão Salvar só aparece quando o valor mudou (em vez de ficar cinza sem explicação).
function TargetInput({ row, month }: { row: TargetBoardRowDto; month: string }) {
  const save = useSetTarget();
  const toast = useToast();
  const initial = row.target == null ? '' : row.target.toFixed(2).replace('.', ',');
  const [value, setValue] = useState(initial);
  const [error, setError] = useState<string>();
  const changed = value.trim() !== initial;

  async function submit() {
    const amount = Number(value.replace(/\./g, '').replace(',', '.'));
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Informe um valor maior que zero, ex.: 15000,00.');
      return;
    }
    setError(undefined);
    try {
      await save.mutateAsync({ sellerId: row.sellerId, month, amount });
      toast.success(`Meta de ${row.sellerName} salva. A mudança foi registrada na auditoria.`);
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <form
      className="flex flex-col gap-1"
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <span className="flex items-center gap-2">
        <span className="text-sm text-muted">R$</span>
        <input
          aria-label={`Meta de ${row.sellerName}`}
          inputMode="decimal"
          placeholder="sem meta"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          className={cn('h-8 w-28 rounded-md border bg-surface px-2 text-sm tabular', error ? 'border-error' : 'border-border')}
        />
        {changed && <Button size="sm" type="submit" loading={save.isPending}>Salvar</Button>}
      </span>
      {error && <span className="text-xs text-error">{error}</span>}
    </form>
  );
}
