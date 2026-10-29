import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import type { PlanningDto, RequestAlertsDto, Role, SellerPanelDto, StockAlertsDto, TargetBoardDto } from '@/data/dto';
import { http } from '@/data/http';
import { formatMoney } from '@/lib/format';
import { Button } from '../atoms/Button';
import { Modal } from '../molecules/Modal';

const MANAGER_LIKE: Role[] = ['MANAGER', 'DEVELOPER', 'MARKETING_DEV'];
const STOCK_LIKE: Role[] = ['STOCK_KEEPER', 'PURCHASING'];

interface Item { text: string; to: string }

// Aviso de pendências ao entrar (padrão da Plataforma de Operações): uma vez por sessão, só se houver o que fazer.
// Cada linha leva direto à tela certa. Não aparece no "Ver como".
export function PendingOnLogin() {
  const { me, role } = useSession();
  const key = me ? `orden.pendencias.${me.id}` : '';
  const [dismissed, setDismissed] = useState(() => {
    try { return !key || sessionStorage.getItem(key) === '1'; } catch { return false; }
  });
  const active = !!me && !me.viewAs && !dismissed && !!role;
  const isManager = !!role && (MANAGER_LIKE.includes(role) || !!me?.superAdmin);
  const isStock = !!role && STOCK_LIKE.includes(role);
  const isSeller = role === 'SELLER';

  const alerts = useQuery({ queryKey: ['pending', 'alerts'], enabled: active,
    queryFn: () => http.get<RequestAlertsDto>('/crm/requests/alerts') });
  const stock = useQuery({ queryKey: ['pending', 'stock'], enabled: active && (isManager || isStock),
    queryFn: () => http.get<StockAlertsDto>('/crm/stock/alerts') });
  const plan = useQuery({ queryKey: ['pending', 'planning'], enabled: active && (isManager || isStock),
    queryFn: () => http.get<PlanningDto>('/crm/stock/planning') });
  const board = useQuery({ queryKey: ['pending', 'targets'], enabled: active && isManager,
    queryFn: () => http.get<TargetBoardDto>('/crm/targets') });
  const panel = useQuery({ queryKey: ['pending', 'panel'], enabled: active && isSeller,
    queryFn: () => http.get<SellerPanelDto>('/crm/my-panel') });

  const items: Item[] = [];
  const a = alerts.data;
  if (a && a.count > 0) {
    items.push(a.kind === 'OPEN_REQUESTS'
      ? { text: `${a.count} chamado(s) de peça em falta esperando resposta`, to: '/chamados' }
      : { text: `${a.count} novidade(s) nos seus chamados`, to: '/chamados' });
  }
  if (stock.data && stock.data.outOfStock.length > 0) {
    items.push({ text: `${stock.data.outOfStock.length} produto(s) sem estoque`, to: '/estoque' });
  }
  const ps = plan.data?.summary;
  if (ps && ps.lateOrders > 0) items.push({ text: `${ps.lateOrders} pedido(s) de compra atrasado(s)`, to: '/pedidos-compra' });
  if (ps && ps.critical > 0) items.push({ text: `${ps.critical} peça(s) acabam em até 7 dias`, to: '/reposicao' });
  if (board.data && !board.data.closed) {
    const semMeta = board.data.rows.filter((r) => r.status === 'NO_TARGET').length;
    if (semMeta > 0) items.push({ text: `${semMeta} vendedor(es) sem meta neste mês`, to: '/metas' });
  }
  const p = panel.data;
  if (p) {
    if (p.status === 'NO_TARGET') items.push({ text: 'Sua meta deste mês ainda não foi definida: fale com o gerente', to: '/meu-painel' });
    else if (p.remaining && p.remaining > 0 && p.neededPerDay != null) {
      items.push({ text: `Faltam ${formatMoney(p.remaining)} para a sua meta: ${formatMoney(p.neededPerDay)} por dia útil`, to: '/meu-painel' });
    }
  }

  const loading = [alerts, stock, plan, board, panel].some((q) => q.isFetching);
  const show = active && !loading && items.length > 0;

  function close() {
    try { sessionStorage.setItem(key, '1'); } catch { /* sem storage: aparece de novo na próxima tela */ }
    setDismissed(true);
  }

  // Sair do sistema e entrar com outra pessoa reabre o aviso para ela.
  useEffect(() => {
    try { setDismissed(!key || sessionStorage.getItem(key) === '1'); } catch { setDismissed(false); }
  }, [key]);

  if (!show) return null;
  return (
    <Modal open title={`Olá, ${me!.fullName.split(' ')[0]}! Você tem pendências`} onClose={close}
      footer={<Button variant="secondary" onClick={close}>Ver depois</Button>}>
      <ul className="flex flex-col gap-2">
        {items.map((i) => (
          <li key={i.text}>
            <Link to={i.to} onClick={close} className="block rounded-lg border border-border px-3 py-2.5 text-sm hover:border-primary hover:bg-primary-soft/40">
              {i.text} <span aria-hidden className="text-muted">›</span>
            </Link>
          </li>
        ))}
      </ul>
    </Modal>
  );
}
