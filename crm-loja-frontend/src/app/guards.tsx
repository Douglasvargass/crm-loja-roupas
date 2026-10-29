import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import type { Role } from '@/data/dto';
import { homeOf } from '@/domain/permissions';
import { useSession } from './context/SessionContext';

function FullScreenLoading() {
  return (
    <div className="flex h-full items-center justify-center text-muted" aria-busy="true">
      Carregando…
    </div>
  );
}

export function RequireAuth() {
  const { state } = useSession();
  const location = useLocation();
  if (state.status === 'loading') return <FullScreenLoading />;
  if (state.status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

// HU34: no primeiro acesso, a troca de senha vem antes de qualquer outra tela.
export function RequireActivePassword() {
  const { me } = useSession();
  if (me?.mustChangePassword) return <Navigate to="/trocar-senha" replace />;
  return <Outlet />;
}

// Espaço do cliente (spec crm/07): cliente só entra nas telas da loja; a equipe só nas internas.
export function RequireStaff() {
  const { role } = useSession();
  if (role === 'CUSTOMER') return <Navigate to="/loja" replace />;
  return <Outlet />;
}

export function RequireCustomer() {
  const { role } = useSession();
  if (role !== 'CUSTOMER') return <Navigate to={role ? homeOf(role) : '/login'} replace />;
  return <Outlet />;
}

export function RequireRole({ allow, children }: { allow: (r: Role) => boolean; children: ReactNode }) {
  const { role } = useSession();
  if (!role) return <NoAccess />;
  if (!allow(role)) return <Navigate to={homeOf(role)} replace />;
  return <>{children}</>;
}

function NoAccess() {
  return (
    <div className="mx-auto max-w-md p-8 text-center">
      <h1 className="text-lg font-semibold">Sem acesso ao CRM</h1>
      <p className="mt-2 text-muted">Sua conta ainda não tem um papel na loja. Fale com o gerente.</p>
    </div>
  );
}
