import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { roleLabel } from '@/data/adapters';
import type { Role } from '@/data/dto';
import { can } from '@/domain/permissions';
import { cn } from '@/lib/cn';
import { Icon, type IconName } from '../atoms/Icon';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  allow: (r: Role) => boolean;
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard', allow: can.dashboard },
  { to: '/vendas', label: 'Vendas', icon: 'sales', allow: can.sales },
  { to: '/clientes', label: 'Clientes', icon: 'customers', allow: can.customers },
  { to: '/produtos', label: 'Produtos', icon: 'products', allow: can.products },
  { to: '/categorias', label: 'Categorias', icon: 'categories', allow: can.categories },
  { to: '/estoque', label: 'Estoque', icon: 'stock', allow: can.stock },
  { to: '/usuarios', label: 'Usuários', icon: 'users', allow: can.users },
  { to: '/assistente', label: 'Assistente IA', icon: 'assistant', allow: can.assistant },
];

// Menu lateral fixo com os módulos que o papel permite (HU31). No celular vira gaveta.
export function AppShell() {
  const { me, role, logout } = useSession();
  const [open, setOpen] = useState(false);
  const items = NAV.filter((i) => role && i.allow(role));

  const sidebar = (
    <nav className="flex h-full flex-col gap-1 p-3" aria-label="Menu principal">
      <div className="mb-4 flex items-center gap-2 px-2 pt-1">
        <img src="/favicon.svg" alt="" className="size-8" />
        <div className="leading-tight">
          <div className="text-sm font-semibold">Loja de Roupas</div>
          <div className="text-xs text-muted">CRM</div>
        </div>
      </div>
      {items.map((i) => (
        <NavLink
          key={i.to}
          to={i.to}
          end={i.to === '/'}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              isActive ? 'bg-primary-soft text-primary' : 'text-muted hover:bg-surface-2 hover:text-text',
            )
          }
        >
          <Icon name={i.icon} />
          {i.label}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 border-r border-border bg-surface lg:block">{sidebar}</aside>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button type="button" className="absolute inset-0 bg-black/40" aria-label="Fechar menu" onClick={() => setOpen(false)} />
          <aside className="relative h-full w-64 bg-surface shadow-xl">{sidebar}</aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between gap-3 border-b border-border bg-surface px-4">
          <button type="button" className="rounded-md p-1.5 hover:bg-surface-2 lg:hidden" onClick={() => setOpen(true)} aria-label="Abrir menu">
            <Icon name="menu" />
          </button>
          <div className="ml-auto flex items-center gap-3">
            <div className="text-right leading-tight">
              <div className="text-sm font-medium">{me?.fullName}</div>
              <div className="text-xs text-muted">
                {role ? roleLabel[role] : 'Sem papel'} ·{' '}
                <Link to="/trocar-senha" className="hover:text-primary hover:underline">Trocar senha</Link>
              </div>
            </div>
            <button
              type="button"
              onClick={() => void logout()}
              className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-muted hover:bg-surface-2 hover:text-text"
            >
              <Icon name="logout" className="size-4" />
              Sair
            </button>
          </div>
        </header>
        <main className="min-w-0 flex-1 overflow-y-auto px-4 py-6 sm:px-6">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
