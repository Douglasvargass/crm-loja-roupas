import { Link, useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { can } from '@/domain/permissions';
import { coverageLabel, coverageTone } from '@/data/adapters';
import type { PurchaseOrderDto } from '@/data/dto';
import { usePlanning, usePurchaseOrders } from '@/data/hooks';
import { formatDate, formatDateTime, formatMoney } from '@/lib/format';
import { Badge } from '../atoms/Badge';
import { Button } from '../atoms/Button';
import { Skeleton } from '../atoms/Skeleton';
import { KpiCard } from '../molecules/KpiCard';
import { Card } from '../templates/Page';

// Bloco "Estoque" do Dashboard (pedido do Douglas, 29/10/2026): o gerente vê junto das vendas; Operações e Compras e o
// estoquista veem só ele (o faturamento continua só do gerente). Usa as mesmas contas da Reposição (/crm/stock/planning,
// spec crm/11) e os pedidos de compra abertos, então os números batem com as telas de Reposição e Pedidos de compra.
// Nenhum valor de venda aparece aqui: "valor em estoque" é o estoque × preço de etiqueta, e "mais saem" é em unidades.
export function StockOverview() {
  const { role } = useSession();
  const navigate = useNavigate();
  const buyer = !!role && can.buy(role);
  const plan = usePlanning(30);
  // Uma consulta por etapa do pedido de compra (V11), para cada lista do painel.
  const ordered = usePurchaseOrders('OPEN', false, 0);
  const transit = usePurchaseOrders('IN_TRANSIT', false, 0);
  const checking = usePurchaseOrders('CHECKING', false, 0);
  const received = usePurchaseOrders('RECEIVED', false, 0);

  if (plan.isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => <Skeleton key={i} className="h-24" />)}
      </div>
    );
  }
  if (!plan.data) return <p className="text-sm text-error">Não consegui carregar o estoque agora. Tente atualizar a página.</p>;

  const { summary: s, items } = plan.data;
  const units = items.reduce((n, i) => n + i.stock, 0);
  const value = items.reduce((n, i) => n + i.stock * i.price, 0);
  const urgent = items.filter((i) => i.status === 'OUT' || i.status === 'CRITICAL' || i.status === 'ATTENTION').slice(0, 6);
  // Sugestão de pedido (pedido do Douglas, 29/10): as peças que precisam de compra, com a quantidade já calculada pela
  // Reposição (cobrir 30 dias + mínimo + chamados − estoque − a caminho). O botão abre o pedido pronto, como na Reposição.
  const suggestion = items.filter((i) => i.needsOrder);
  const topMoving = [...items].filter((i) => i.soldInWindow > 0).sort((a, b) => b.soldInWindow - a.soldInWindow).slice(0, 5);
  const byDate = (l?: PurchaseOrderDto[]) => [...(l ?? [])].sort((a, b) => a.expectedDate.localeCompare(b.expectedDate)).slice(0, 5);
  const orderedList = byDate(ordered.data?.content);
  const transitList = byDate(transit.data?.content);
  const checkingList = (checking.data?.content ?? []).slice(0, 5);
  const invoices = (received.data?.content ?? []).slice(0, 5);   // já vem da mais recente para a mais antiga

  return (
    <section aria-labelledby="estoque-titulo" className="flex flex-col gap-4">
      <h2 id="estoque-titulo" className="text-lg font-semibold">Estoque</h2>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <KpiCard label="Peças em estoque" value={units.toLocaleString('pt-BR')} hint={`${items.length} produtos ativos`} to="/estoque" />
        <KpiCard label="Valor em estoque" value={formatMoney(value)} hint="pelo preço de etiqueta" />
        <KpiCard label="Zerados" value={s.outOfStock} tone={s.outOfStock > 0 ? 'error' : 'default'} to="/reposicao" />
        <KpiCard label="Acabam em 7 dias" value={s.critical} tone={s.critical > 0 ? 'error' : 'default'} hint="ou já no estoque mínimo" to="/reposicao" />
        <KpiCard label="Para pedir" value={s.toOrder} tone={s.toOrder > 0 ? 'warning' : 'default'} hint="o que está a caminho não basta" to="/reposicao" />
        <KpiCard label="Chamados abertos" value={s.openRequests} tone={s.openRequests > 0 ? 'warning' : 'default'} hint="peças que clientes pediram" to="/chamados" />
        <KpiCard label="Dinheiro parado" value={formatMoney(s.staleValue)} hint={`${s.stale} peça(s) sem venda em 30 dias`} />
        <KpiCard label="Pedidos feitos" value={s.ordered} hint={s.lateOrders > 0 ? `${s.lateOrders} atrasado(s)` : 'aguardando o fornecedor'}
          tone={s.lateOrders > 0 ? 'error' : 'default'} to="/pedidos-compra" />
        <KpiCard label="Em trânsito" value={s.inTransit} hint="saíram do fornecedor" to="/pedidos-compra" />
        <KpiCard label="Em conferência" value={s.checking} tone={s.checking > 0 ? 'warning' : 'default'} hint="chegaram, falta dar entrada" to="/pedidos-compra" />
        <KpiCard label="Notas que deram entrada" value={s.receivedThisWeek} hint="nos últimos 7 dias" to="/pedidos-compra" />
      </div>

      <Card
        title={`Sugestão de pedido${suggestion.length > 0 ? ` (${suggestion.length} peça(s))` : ''}`}
        actions={suggestion.length > 0 && buyer && (
          <Button size="sm" onClick={() => navigate(`/pedidos-compra?novo=${encodeURIComponent(suggestion.map((i) => `${i.productId}:${i.suggestedQty}`).join(','))}`)}>
            Montar pedido com estas peças
          </Button>
        )}
      >
        {suggestion.length === 0 ? <p className="p-4 text-sm text-muted">Nada para pedir agora: o estoque e o que está a caminho cobrem os próximos 30 dias.</p> : (
          <>
            <ul className="divide-y divide-border">
              {suggestion.slice(0, 8).map((i) => (
                <li key={i.productId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{i.name} <span className="text-xs font-normal text-muted">{i.code}</span></span>
                    <span className="text-xs text-muted">
                      estoque {i.stock} · {i.stock === 0 ? 'já acabou' : i.daysLeft != null ? `acaba em ${i.daysLeft} dia(s)` : 'sem previsão'}
                      {i.incoming > 0 && ` · ${i.incoming} a caminho`}
                      {i.openRequestsQty > 0 && ` · ${i.openRequestsQty} em chamados`}
                    </span>
                  </span>
                  <span className="flex items-center gap-2">
                    <Badge tone={coverageTone[i.status]}>{coverageLabel[i.status]}</Badge>
                    <span className="tabular w-24 text-right font-semibold">pedir {i.suggestedQty}</span>
                  </span>
                </li>
              ))}
            </ul>
            <p className="px-4 py-2 text-xs text-muted">
              {suggestion.length > 8 && `Mostrando 8 de ${suggestion.length}. `}
              Quantidade para cobrir 30 dias, mais o estoque mínimo e os chamados abertos, descontando o que tem e o que já vem.
              {!buyer && ' Quem monta o pedido é Operações e Compras ou o gerente.'}
              {' '}<Link to="/reposicao" className="text-primary hover:underline">Ver tudo na Reposição ›</Link>
            </p>
          </>
        )}
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Acabam primeiro" actions={<Link to="/reposicao" className="text-xs text-primary hover:underline">Ver Reposição ›</Link>}>
          {urgent.length === 0 ? <p className="p-4 text-sm text-muted">Nenhuma peça acabando. Tudo em ordem!</p> : (
            <ul className="divide-y divide-border">
              {urgent.map((i) => (
                <li key={i.productId} className="flex items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{i.name}</span>
                    <span className="text-xs text-muted">
                      estoque {i.stock} · {i.stock === 0 ? 'já acabou' : i.daysLeft != null ? `acaba em ${i.daysLeft} dia(s)` : 'sem previsão'}
                      {i.incoming > 0 && ` · ${i.incoming} a caminho`}
                    </span>
                  </span>
                  <Badge tone={coverageTone[i.status]}>{coverageLabel[i.status]}</Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Mais saem (30 dias)">
          {topMoving.length === 0 ? <p className="p-4 text-sm text-muted">Sem vendas nos últimos 30 dias.</p> : (
            <ul className="divide-y divide-border">
              {topMoving.map((i) => (
                <li key={i.productId} className="flex items-center justify-between gap-2 px-4 py-2.5 text-sm">
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{i.name}</span>
                    <span className="text-xs text-muted">{i.dailyAvg.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} por dia · estoque {i.stock}</span>
                  </span>
                  <span className="tabular shrink-0 font-semibold">{i.soldInWindow} un.</span>
                </li>
              ))}
            </ul>
          )}
        </Card>

      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <OrderList title="Pedidos feitos" empty="Nenhum pedido aguardando o fornecedor." list={orderedList}
          detail={(o) => `chega ${formatDate(o.expectedDate)}`} />
        <OrderList title="Em trânsito" empty="Nada em trânsito agora." list={transitList}
          detail={(o) => `chega ${formatDate(o.expectedDate)}${o.invoiceNumber ? ` · NF ${o.invoiceNumber}` : ''}`} />
        <OrderList title="Em conferência" empty="Nada em conferência agora." list={checkingList}
          detail={(o) => `chegou ${o.arrivedAt ? formatDateTime(o.arrivedAt) : ''}${o.invoiceNumber ? ` · NF ${o.invoiceNumber}` : ''}`} />
        <OrderList title="Notas que deram entrada" empty="Nenhuma entrada por pedido de compra ainda." list={invoices}
          detail={(o) => `NF ${o.invoiceNumber ?? '—'} · ${o.closedAt ? formatDateTime(o.closedAt) : ''}`} />
      </div>
    </section>
  );
}

// Lista curta de pedidos de compra de uma etapa, com o link para a tela completa.
function OrderList({ title, empty, list, detail }: {
  title: string; empty: string; list: PurchaseOrderDto[]; detail: (o: PurchaseOrderDto) => string;
}) {
  return (
    <Card title={title} actions={<Link to="/pedidos-compra" className="text-xs text-primary hover:underline">Ver pedidos ›</Link>}>
      {list.length === 0 ? <p className="p-4 text-sm text-muted">{empty}</p> : (
        <ul className="divide-y divide-border">
          {list.map((o) => (
            <li key={o.id} className="flex items-center justify-between gap-2 px-4 py-2.5 text-sm">
              <span className="min-w-0">
                <span className="block truncate font-medium">nº {o.id} · {o.supplier}</span>
                <span className="text-xs text-muted">{detail(o)}</span>
              </span>
              <span className="shrink-0 text-right">
                <span className="block tabular">{o.totalUnits} un.</span>
                {o.late && <Badge tone="error">Atrasado</Badge>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
