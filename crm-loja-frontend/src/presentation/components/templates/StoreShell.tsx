import { Link, NavLink, Outlet } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useCart } from '@/data/hooks';
import { cn } from '@/lib/cn';
import { Icon } from '../atoms/Icon';
import { Logo } from '../atoms/Logo';
import { useStorePreview } from '../../pages/store/previewContext';
import { ThalitaChat } from '../organisms/ThalitaChat';

// Caminhos relativos à base: /loja para o cliente, /previa-loja para a Prévia da loja da equipe.
const LINKS = [
  { to: '', label: 'Vitrine', end: true },
  { to: '/pedidos', label: 'Meus pedidos', end: false },
  { to: '/meus-dados', label: 'Meus dados', end: true },
];

// Espaço do cliente (spec crm/07): layout próprio, sem o menu da equipe e sem o sino. A Thalita aparece na versão do
// cliente (só vitrine, os pedidos dele e pedir peça).
export function StoreShell() {
  const { me, logout } = useSession();
  const pv = useStorePreview();
  const base = pv ? pv.base : '/loja';
  const cart = useCart(!pv);
  const count = pv ? pv.count : cart.data?.count ?? 0;
  return (
    <div className="flex min-h-full flex-col">
      <header className="chrome-light sticky top-0 z-40 border-b border-border bg-surface">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
          <Link to={base} aria-label="Orden: vitrine" className="flex items-center">
            <Logo className="h-9" plate={false} />
          </Link>
          <nav className="hidden gap-1 sm:flex" aria-label="Loja">
            {LINKS.map((l) => (
              <NavLink key={l.to} to={base + l.to} end={l.end}
                className={({ isActive }) => cn('rounded-lg px-3 py-2 text-sm font-medium',
                  isActive ? 'bg-primary-soft text-primary' : 'text-muted hover:bg-surface-2 hover:text-text')}>
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <Link to={`${base}/carrinho`} className="relative flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium hover:bg-surface-2"
              aria-label={`Carrinho com ${count} peça(s)`}>
              <Icon name="sales" className="size-5" />
              <span className="hidden sm:inline">Carrinho</span>
              {count > 0 && (
                <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-[11px] font-semibold text-on-primary">{count}</span>
              )}
            </Link>
            {pv ? (
              <Link to="/" aria-label="Sair da prévia" title="Sair da prévia e voltar ao sistema"
                className="flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-surface-2 hover:text-text">
                {/* Ícone do painel, não o de sair: na prévia ninguém sai da conta, só volta para o sistema da equipe. */}
                <Icon name="dashboard" className="size-4" />
                <span className="hidden sm:inline">Sair da prévia</span>
              </Link>
            ) : (<>
            <span className="hidden text-right text-xs leading-tight text-muted md:block">
              {me?.fullName}<br />
              <Link to="/trocar-senha" className="hover:text-primary hover:underline">Trocar senha</Link>
            </span>
            <button type="button" onClick={() => void logout()} aria-label="Sair"
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-surface-2 hover:text-text">
              <Icon name="logout" className="size-4" />
              <span className="hidden sm:inline">Sair</span>
            </button>
            </>)}
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto border-t border-border px-2 py-1 sm:hidden" aria-label="Loja">
          {LINKS.map((l) => (
            <NavLink key={l.to} to={base + l.to} end={l.end}
              className={({ isActive }) => cn('shrink-0 rounded-md px-3 py-1.5 text-sm', isActive ? 'bg-primary-soft text-primary' : 'text-muted')}>
              {l.label}
            </NavLink>
          ))}
        </nav>
      </header>
      {/* Faixa no padrão do "Ver como" da Plataforma de Operações (pedido do Douglas, 29/10): azul claro de propósito,
          fora da paleta da marca (pedido do Douglas, 29/10), para chamar atenção, ícone de olho e a saída sempre à vista à direita. */}
      {pv && (
        <div role="status" className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-sky-300 bg-sky-100 px-4 py-2.5 text-sm text-sky-900">
          <span className="flex items-center gap-2">
            <Icon name="eye" className="size-4 shrink-0" />
            <span><strong>Prévia da loja</strong> · é assim que o cliente vê o site · somente leitura, nada é gravado</span>
          </span>
          <Link to="/" className="shrink-0 rounded-lg border border-sky-700/50 bg-white/70 px-3 py-1 text-xs font-semibold text-sky-900 hover:bg-white">
            Sair da prévia
          </Link>
        </div>
      )}
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>
      {/* Thalita do cliente (29/10/2026). Na prévia da equipe ela não aparece: a conversa é do cliente logado. */}
      {!pv && <ThalitaChat />}
    </div>
  );
}
