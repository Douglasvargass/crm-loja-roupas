import { authEvents } from './auth-events';

// Único cliente HTTP. Tokens vivem em cookie httpOnly: aqui só mandamos credentials: 'include'.
// Em 401 tenta renovar a sessão UMA vez e repete a chamada. A renovação é deduplicada (promessa
// compartilhada na aba + reserva no localStorage entre abas), porque dois refresh em paralelo com o
// mesmo token disparam a detecção de reuso no back-end e derrubam a sessão.

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: string,
  ) {
    super(message);
  }
}

// '/api' no Vite (o proxy tira o prefixo). No deploy único o back-end serve as telas na mesma origem,
// então o build usa VITE_API_BASE='' e chama /auth, /crm… direto.
const BASE = import.meta.env.VITE_API_BASE ?? '/api';
const LOCK_KEY = 'crm.refresh-lock';
const LOCK_MS = 30_000;

let refreshing: Promise<boolean> | null = null;

function otherTabIsRefreshing() {
  try {
    const until = Number(localStorage.getItem(LOCK_KEY) ?? 0);
    return until > Date.now();
  } catch {
    return false;
  }
}

function setLock(on: boolean) {
  try {
    if (on) localStorage.setItem(LOCK_KEY, String(Date.now() + LOCK_MS));
    else localStorage.removeItem(LOCK_KEY);
  } catch {
    /* storage bloqueado: a deduplicação na aba ainda vale */
  }
}

export function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    // Outra aba já está renovando: espera ela terminar e usa os cookies novos.
    if (otherTabIsRefreshing()) {
      await new Promise((r) => setTimeout(r, 1500));
      return true;
    }
    setLock(true);
    try {
      const res = await fetch(`${BASE}/auth/refresh`, { method: 'POST', credentials: 'include' });
      return res.ok;
    } catch {
      return false;
    } finally {
      setLock(false);
    }
  })().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as { error?: string; code?: string };
    return new ApiError(res.status, body.error ?? 'Algo deu errado. Tente de novo.', body.code);
  } catch {
    // Resposta sem o JSON {error, code}: veio de fora dos controllers (proxy, CORS, servidor errado).
    return new ApiError(
      res.status,
      res.status >= 500 ? 'Erro interno. Tente de novo.' : `O servidor recusou a requisição (HTTP ${res.status}).`,
    );
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;

function url(path: string, query?: Query) {
  const qs = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  const s = qs.toString();
  return `${BASE}${path}${s ? `?${s}` : ''}`;
}

export async function request<T>(
  method: string,
  path: string,
  options: { body?: unknown; query?: Query; retry?: boolean } = {},
): Promise<T> {
  const init: RequestInit = {
    method,
    credentials: 'include',
    headers: options.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  };
  let res: Response;
  try {
    res = await fetch(url(path, options.query), init);
  } catch {
    throw new ApiError(0, 'Sem conexão com o servidor. Verifique se o back-end está rodando.');
  }

  if (res.status === 401 && options.retry !== false && !path.startsWith('/auth/')) {
    if (await refreshSession()) return request<T>(method, path, { ...options, retry: false });
    authEvents.emitSessionLost();
  }
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const http = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
  del: <T>(path: string) => request<T>('DELETE', path),
};
