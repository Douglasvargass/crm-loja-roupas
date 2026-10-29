import type { DemoAgentDayDto } from '@/data/dto';
import { useDemoAgent } from '@/data/hooks';
import { formatDate, formatMoney } from '@/lib/format';
import { Badge } from '../atoms/Badge';
import { Card } from '../templates/Page';

const FAMILY: Record<string, string> = { CAM: 'Camisetas', CAL: 'Calças', VES: 'Vestidos', JAQ: 'Jaquetas', ACE: 'Acessórios' };

function weekday(iso: string) {
  return new Date(`${iso}T12:00:00`).toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '');
}

function SourceBadge({ d }: { d: DemoAgentDayDto }) {
  if (d.status === 'SKIPPED') return <Badge tone="neutral">Pulado</Badge>;
  return d.source === 'AI' ? <Badge tone="info">IA</Badge> : <Badge tone="neutral">Padrão</Badge>;
}

// Spec crm/08: o que o agente de demonstração fez em cada dia e por quê. Só aparece na demonstração.
export function DemoAgentDiary() {
  const q = useDemoAgent();
  if (!q.data?.enabled) return null;
  const { days, aiEnabled } = q.data;
  return (
    <Card
      title="Diário do agente de vendas (demonstração)"
      actions={<span className="text-xs text-muted">{aiEnabled ? 'IA ligada' : 'IA desligada: dias no padrão da loja'}</span>}
    >
      <p className="border-b border-border px-4 py-2.5 text-xs text-muted">
        Todo dia que passou, o agente cria vendas de demonstração parecidas com as reais. A IA só escolhe o clima do dia
        (mais ou menos movimento e uma categoria em destaque); quem grava as vendas é o sistema, sem mexer no que é real.
      </p>
      {days.length === 0 ? (
        <p className="p-6 text-center text-sm text-muted">O agente ainda não fechou nenhum dia. Ele começa no dia seguinte ao histórico.</p>
      ) : (
        <ul className="divide-y divide-border">
          {days.map((d) => (
            <li key={d.day} className="flex flex-col gap-1 px-4 py-2.5 text-sm sm:flex-row sm:items-center sm:gap-3">
              <span className="w-28 shrink-0 tabular">
                {formatDate(d.day)} <span className="text-xs text-muted">{weekday(d.day)}</span>
              </span>
              <span className="shrink-0"><SourceBadge d={d} /></span>
              <span className="min-w-0 flex-1">
                {d.reason}
                {d.highlightFamily && (
                  <span className="text-xs text-muted"> · destaque: {FAMILY[d.highlightFamily] ?? d.highlightFamily}</span>
                )}
              </span>
              {d.status === 'GENERATED' && (
                <span className="shrink-0 tabular text-muted">{d.salesCreated} vendas · {formatMoney(d.revenue)}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
