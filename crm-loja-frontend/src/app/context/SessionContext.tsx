import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authEvents } from '@/data/auth-events';
import { authApi } from '@/data/hooks';
import { refreshSession } from '@/data/http';
import type { MeDto, Role } from '@/data/dto';

// Nada de token em storage: ao abrir, /auth/me decide entre carregando, logado e anônimo.
type State = { status: 'loading' } | { status: 'anonymous' } | { status: 'authenticated'; me: MeDto };

interface SessionValue {
  state: State;
  me: MeDto | null;
  role: Role | null;
  login: (login: string, password: string) => Promise<MeDto>;
  logout: () => Promise<void>;
  setMe: (me: MeDto) => void;
  // Recarrega quem está logado e limpa o cache: usado ao entrar e sair do "Ver como" (a identidade muda).
  reloadMe: () => Promise<MeDto | null>;
}

const SessionContext = createContext<SessionValue | null>(null);

const PROACTIVE_REFRESH_MS = 8 * 60_000; // o access dura 10 min

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ status: 'loading' });
  const qc = useQueryClient();

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const me = await authApi.me();
        if (alive) setState({ status: 'authenticated', me });
      } catch {
        // access vencido mas refresh ainda vale: tenta renovar antes de mandar para o login.
        const ok = await refreshSession();
        const me = ok ? await authApi.me().catch(() => null) : null;
        if (alive) setState(me ? { status: 'authenticated', me } : { status: 'anonymous' });
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  useEffect(
    () =>
      authEvents.onSessionLost(() => {
        qc.clear();
        setState({ status: 'anonymous' });
      }),
    [qc],
  );

  const authenticated = state.status === 'authenticated';
  useEffect(() => {
    if (!authenticated) return;
    const t = setInterval(() => void refreshSession(), PROACTIVE_REFRESH_MS);
    return () => clearInterval(t);
  }, [authenticated]);

  const login = useCallback(async (login: string, password: string) => {
    const me = await authApi.login(login, password);
    setState({ status: 'authenticated', me });
    return me;
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout().catch(() => undefined);
    qc.clear();
    setState({ status: 'anonymous' });
  }, [qc]);

  const setMe = useCallback((me: MeDto) => setState({ status: 'authenticated', me }), []);

  const reloadMe = useCallback(async () => {
    qc.clear();
    const me = await authApi.me().catch(() => null);
    setState(me ? { status: 'authenticated', me } : { status: 'anonymous' });
    return me;
  }, [qc]);

  const me = state.status === 'authenticated' ? state.me : null;
  return (
    <SessionContext.Provider value={{ state, me, role: me?.role ?? null, login, logout, setMe, reloadMe }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession fora do SessionProvider');
  return ctx;
}
