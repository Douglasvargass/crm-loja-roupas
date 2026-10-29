import { useState } from 'react';
import { useToast } from '@/app/context/ToastContext';
import { targetStatusLabel, targetStatusTone } from '@/data/adapters';
import type { TargetBoardDto, TargetBoardRowDto, TargetSuggestionRowDto, TargetSuggestionsDto } from '@/data/dto';
import { useSetTarget, useTargetSuggestions, useTargets } from '@/data/hooks';
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
function TargetsPageBody() {
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
  const [growth, setGrowth] = useState(0);
  const sug = useTargetSuggestions(b.month, growth, b.editable);
  const byId = new Map((sug.data?.rows ?? []).map((r) => [r.sellerId, r]));
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
      {b.editable && <SuggestionBar month={b.month} growth={growth} setGrowth={setGrowth} sug={sug.data} error={sug.error} />}
      <Card>
        <BoardTable rows={b.rows} month={b.month} editable={b.editable} suggestions={byId} />
      </Card>
      <p className="text-xs text-muted">
        Conta a venda confirmada no mês por quem abriu a venda. "No ritmo": mantendo a média por dia útil (segunda a
        sábado), o vendedor alcança a meta. "Atenção": chega entre 80% e 100%. Cada mudança de meta fica registrada na
        auditoria.
      </p>
    </div>
  );
}

function BoardTable({ rows, month, editable, suggestions }: { rows: TargetBoardRowDto[]; month: string; editable: boolean; suggestions: Map<number, TargetSuggestionRowDto> }) {
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
          cell: (r) => (editable ? <TargetInput key={`${r.sellerId}-${r.target ?? ''}`} row={r} month={month} /> : r.target == null ? <span className="text-muted">sem meta</span> : <span className="tabular">{formatMoney(r.target)}</span>),
        },
        ...(editable
          ? [{ header: 'Sugestão pela previsão', cell: (r: TargetBoardRowDto) => <SuggestionCell row={r} month={month} s={suggestions.get(r.sellerId)} /> }]
          : []),
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

// Spec crm/12, REQ-03: meta sugerida = previsão do mês × parte do vendedor nos últimos 90 dias × (1 + crescimento).
function SuggestionBar({ month, growth, setGrowth, sug, error }: {
  month: string; growth: number; setGrowth: (g: number) => void; sug?: TargetSuggestionsDto; error: unknown;
}) {
  const save = useSetTarget();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const toApply = (sug?.rows ?? []).filter((r) => r.suggested != null && r.suggested !== r.currentTarget);

  async function applyAll() {
    try {
      for (const r of toApply) await save.mutateAsync({ sellerId: r.sellerId, month, amount: r.suggested! });
      toast.success(`${toApply.length} meta(s) aplicada(s) pela previsão. Cada mudança foi registrada na auditoria.`);
    } catch (err) {
      toast.error(err);
    } finally {
      setConfirming(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="text-sm">
        <p className="font-medium">Sugestão pela previsão de vendas</p>
        {error ? (
          <p className="text-muted">A previsão ainda não está disponível: {(error as Error).message}</p>
        ) : sug ? (
          <p className="text-muted">
            Previsão da loja no mês: <strong className="text-text">{formatMoney(sug.monthForecast)}</strong>. Cada vendedor recebe a parte
            dele no faturamento dos últimos 90 dias{growth !== 0 && <>, com {growth > 0 ? '+' : ''}{growth}% de crescimento</>}.
          </p>
        ) : (
          <p className="text-muted">Calculando…</p>
        )}
      </div>
      <div className="flex items-end gap-2">
        <label className="flex flex-col gap-1 text-xs text-muted">
          Crescimento (%)
          <input
            type="number" min={-20} max={50} step={5} value={growth}
            onChange={(e) => setGrowth(Math.max(-20, Math.min(50, Number(e.target.value) || 0)))}
            className="h-9 w-24 rounded-md border border-border bg-surface px-2 text-sm text-text tabular"
          />
        </label>
        {confirming ? (
          <>
            <Button size="sm" onClick={() => void applyAll()} loading={save.isPending}>Confirmar {toApply.length} meta(s)</Button>
            <Button size="sm" variant="ghost" onClick={() => setConfirming(false)}>Voltar</Button>
          </>
        ) : (
          <Button size="sm" variant="secondary" disabled={toApply.length === 0} onClick={() => setConfirming(true)}
            title={toApply.length === 0 ? 'Todas as metas já estão iguais à sugestão (ou não há sugestão).' : undefined}>
            Aplicar a todos
          </Button>
        )}
      </div>
    </div>
  );
}

function SuggestionCell({ row, month, s }: { row: TargetBoardRowDto; month: string; s?: TargetSuggestionRowDto }) {
  const save = useSetTarget();
  const toast = useToast();
  if (!s) return <span className="text-muted">—</span>;
  if (s.suggested == null) return <span className="text-xs text-muted">{s.reason}</span>;
  const same = row.target != null && row.target === s.suggested;
  return (
    <span className="flex items-center gap-3">
      <span className="flex flex-col leading-tight">
        <span className="tabular whitespace-nowrap">{formatMoney(s.suggested)}</span>
        <span className="text-xs whitespace-nowrap text-muted">{s.sharePercent.toLocaleString('pt-BR')}% da loja</span>
      </span>
      {same ? (
        <span className="text-xs whitespace-nowrap text-muted">igual à meta</span>
      ) : (
        <Button size="sm" variant="secondary" loading={save.isPending}
          onClick={async () => {
            try {
              await save.mutateAsync({ sellerId: row.sellerId, month, amount: s.suggested! });
              toast.success(`Meta de ${row.sellerName} definida pela previsão. A mudança foi registrada na auditoria.`);
            } catch (err) {
              toast.error(err);
            }
          }}>
          Usar
        </Button>
      )}
    </span>
  );
}

// MODO TESTE: cores do Dashboard também nesta tela.
export function TargetsPage() {
  return <div className="cores-dashboard"><TargetsPageBody /></div>;
}
