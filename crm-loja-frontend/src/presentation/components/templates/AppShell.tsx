import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { roleLabel } from '@/data/adapters';
import type { Role } from '@/data/dto';
import { can } from '@/domain/permissions';
import { cn } from '@/lib/cn';
import { Icon, type IconName } from '../atoms/Icon';
import { Logo } from '../atoms/Logo';
import { MenuToggle } from '../atoms/MenuToggle';
import { AlertBell } from '../organisms/AlertBell';
import { PendingOnLogin } from '../organisms/PendingOnLogin';
import { ViewAsBanner, ViewAsButton } from '../organisms/ViewAs';
import { ThalitaChat } from '../organisms/ThalitaChat';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  allow: (r: Role) => boolean;
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard', allow: can.dashboardPage },
  { to: '/meu-painel', label: 'Meu painel', icon: 'dashboard', allow: can.myPanel },
  { to: '/metas', label: 'Metas', icon: 'target', allow: can.targets },
  { to: '/previsao', label: 'Previsão de vendas', icon: 'forecast', allow: can.forecast },
  { to: '/vendas', label: 'Vendas', icon: 'sales', allow: can.sales },
  { to: '/cupons', label: 'Cupons', icon: 'coupon', allow: can.coupons },
  { to: '/clientes', label: 'Clientes', icon: 'customers', allow: can.customers },
  { to: '/catalogo', label: 'Catálogo', icon: 'catalog', allow: can.catalog },
  { to: '/produtos', label: 'Produtos', icon: 'products', allow: can.products },
  { to: '/categorias', label: 'Categorias', icon: 'categories', allow: can.categories },
  { to: '/reposicao', label: 'Reposição', icon: 'trend', allow: can.replenishment },
  { to: '/pedidos-compra', label: 'Pedidos de compra', icon: 'truck', allow: can.replenishment },
  { to: '/estoque', label: 'Estoque', icon: 'stock', allow: can.stock },
  { to: '/chamados', label: 'Chamados', icon: 'alert', allow: can.requests },
  { to: '/usuarios', label: 'Usuários', icon: 'users', allow: can.users },
];

const COLLAPSE_KEY = 'orden.sidebar.collapsed';
const DESKTOP = '(min-width: 1024px)';

function useIsDesktop() {
  const [desktop, setDesktop] = useState(() => window.matchMedia(DESKTOP).matches);
  useEffect(() => {
    const m = window.matchMedia(DESKTOP);
    const onChange = () => setDesktop(m.matches);
    m.addEventListener('change', onChange);
    return () => m.removeEventListener('change', onChange);
  }, []);
  return desktop;
}

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
}

// Cabeçalho com os 3 traços sempre à mão. No computador eles escondem e mostram o menu lateral (a escolha
// fica lembrada); no celular abrem e fecham uma gaveta por cima do conteúdo.
export function AppShell() {
  const { me, role, logout } = useSession();
  const location = useLocation();
  const desktop = useIsDesktop();
  const [collapsed, setCollapsed] = useState(readCollapsed);   // só vale no computador
  const [drawer, setDrawer] = useState(false);                 // só vale no celular
  const items = NAV.filter((i) => role && i.allow(role));

  // Trocou de tela ou de tamanho: a gaveta do celular fecha sozinha.
  useEffect(() => setDrawer(false), [location.pathname, desktop]);

  useEffect(() => {
    if (!drawer) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setDrawer(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [drawer]);

  function toggleMenu() {
    if (desktop) {
      setCollapsed((c) => {
        try { localStorage.setItem(COLLAPSE_KEY, c ? '0' : '1'); } catch { /* sem storage: vale só nesta sessão */ }
        return !c;
      });
    } else {
      setDrawer((d) => !d);
    }
  }

  const menuVisible = desktop ? !collapsed : drawer;
  const nav = (
    <nav className="flex h-full flex-col gap-1 overflow-y-auto p-3" aria-label="Menu principal">
      <div className="mb-3 flex justify-center px-2 pt-2">
        <Logo className="h-24" />
      </div>
      {items.map((i) => (
        <NavLink
          key={i.to}
          to={i.to}
          end={i.to === '/'}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
              isActive ? 'bg-primary-soft text-primary' : 'text-muted hover:bg-surface-2 hover:text-text',
            )
          }
        >
          <Icon name={i.icon} />
          {i.label}
        </NavLink>
      ))}

      {/* Dados da pessoa dentro da gaveta: no celular o cabeçalho não tem espaço para eles. */}
      <div className="mt-auto border-t border-border pt-3 lg:hidden">
        <div className="px-3 text-sm font-medium">{me?.fullName}</div>
        <div className="px-3 pb-2 text-xs text-muted">{role ? roleLabel[role] : 'Sem papel'}</div>
        <Link to="/trocar-senha" className="block rounded-lg px-3 py-2 text-sm text-muted hover:bg-surface-2 hover:text-text">
          Trocar senha
        </Link>
      </div>
    </nav>
  );

  return (
    <div className="flex h-full flex-col">
      <header className="chrome-light relative z-50 flex h-14 shrink-0 items-center gap-2 border-b border-border bg-surface px-2 sm:px-4">
        <MenuToggle
          expanded={menuVisible}
          morph={!desktop}
          label={menuVisible ? 'Esconder menu' : 'Mostrar menu'}
          onClick={toggleMenu}
          controls={desktop ? 'menu-lateral' : 'menu-gaveta'}
        />
        {/* Logo pequena só quando o menu (que já tem a logo grande) está escondido. */}
        <Link to="/" aria-label="Orden: ir para o início" className={cn('flex items-center', menuVisible && desktop && 'invisible')}>
          <Logo className="h-9" plate={false} />
        </Link>
        <div className="ml-auto flex items-center gap-3">
          <ViewAsButton />
          {role && <AlertBell />}
          <div className="hidden text-right leading-tight sm:block">
            <div className="text-sm font-medium">{me?.fullName}</div>
            <div className="text-xs text-muted">
              {role ? roleLabel[role] : 'Sem papel'} ·{' '}
              <Link to="/trocar-senha" className="hover:text-primary hover:underline">Trocar senha</Link>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void logout()}
            aria-label="Sair"
            className="flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-surface-2 hover:text-text"
          >
            <Icon name="logout" className="size-4" />
            <span className="hidden sm:inline">Sair</span>
          </button>
        </div>
      </header>
      <ViewAsBanner />

      <div className="relative flex min-h-0 flex-1">
        {/* Computador: a largura vai a zero quando escondido (some de verdade, sem ocupar espaço). */}
        <aside
          id="menu-lateral"
          inert={collapsed}
          className={cn(
            'chrome-light hidden shrink-0 overflow-hidden border-border bg-surface transition-[width] duration-200 motion-reduce:transition-none lg:block',
            collapsed ? 'w-0' : 'w-64 border-r',
          )}
        >
          <div className="h-full w-64">{nav}</div>
        </aside>

        {/* Celular: gaveta por cima do conteúdo, com fundo escurecido que também fecha ao tocar. */}
        <div
          className={cn(
            'absolute inset-0 z-40 transition-[visibility] duration-200 lg:hidden',
            drawer ? 'visible' : 'invisible pointer-events-none',
          )}
        >
          <button
            type="button"
            aria-label="Fechar menu"
            tabIndex={drawer ? 0 : -1}
            onClick={() => setDrawer(false)}
            className={cn('absolute inset-0 bg-black/45 transition-opacity duration-200 motion-reduce:transition-none', drawer ? 'opacity-100' : 'opacity-0')}
          />
          <aside
            id="menu-gaveta"
            inert={!drawer}
            className={cn(
              'chrome-light relative h-full w-72 max-w-[85vw] bg-surface shadow-2xl transition-transform duration-200 motion-reduce:transition-none',
              drawer ? 'translate-x-0' : '-translate-x-full',
            )}
          >
            {nav}
          </aside>
        </div>

        <main className="min-w-0 flex-1 overflow-y-auto px-4 py-5 sm:px-6 sm:py-6">
          <div className="mx-auto max-w-6xl pb-24">
            <Outlet />
          </div>
        </main>
      </div>

      <PendingOnLogin />
      <ThalitaChat />
    </div>
  );
}
