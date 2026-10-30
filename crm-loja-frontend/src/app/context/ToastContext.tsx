import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

// HU31: todo resultado aparece num aviso verde (sucesso) ou vermelho (erro).
// Acessibilidade (30/10/2026): antes sumia em 4 s, rápido demais para quem lê devagar ou usa leitor de tela. Agora fica
// 8 s (12 s se for erro), PAUSA enquanto o mouse ou o foco estão em cima e tem botão para fechar.
type Toast = { id: number; kind: 'success' | 'error'; text: string };

interface ToastValue {
  success: (text: string) => void;
  error: (err: unknown) => void;
}

const ToastContext = createContext<ToastValue | null>(null);
let seq = 0;

const DURATION = { success: 8000, error: 12000 } as const;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((kind: Toast['kind'], text: string) => {
    const id = ++seq;
    setToasts((t) => [...t, { id, kind, text }]);
  }, []);
  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);

  const success = useCallback((text: string) => push('success', text), [push]);
  const error = useCallback(
    (err: unknown) => push('error', err instanceof Error ? err.message : 'Algo deu errado. Tente de novo.'),
    [push],
  );

  return (
    <ToastContext.Provider value={{ success, error }}>
      {children}
      <div className="pointer-events-none fixed right-4 bottom-4 left-4 z-50 flex flex-col items-end gap-2 sm:left-auto">
        {toasts.map((t) => <ToastItem key={t.id} toast={t} dismiss={dismiss} />)}
      </div>
    </ToastContext.Provider>
  );
}

function ToastItem({ toast: t, dismiss }: { toast: Toast; dismiss: (id: number) => void }) {
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused) return;
    const timer = setTimeout(() => dismiss(t.id), DURATION[t.kind]);
    return () => clearTimeout(timer);
  }, [paused, t.id, t.kind, dismiss]);

  return (
    <div
      role={t.kind === 'error' ? 'alert' : 'status'}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      className={cn(
        'pointer-events-auto flex w-full max-w-sm items-start gap-2 rounded-lg border py-3 pr-2 pl-4 text-sm font-medium shadow-lg',
        t.kind === 'success'
          ? 'border-success/30 bg-success-soft text-success'
          : 'border-error/30 bg-error-soft text-error',
      )}
    >
      <span className="min-w-0 flex-1">{t.text}</span>
      <button
        type="button"
        onClick={() => dismiss(t.id)}
        aria-label="Fechar aviso"
        className="shrink-0 rounded-md px-2 py-0.5 text-base leading-none hover:bg-black/10 focus-visible:ring-2 focus-visible:ring-current focus-visible:outline-none"
      >
        ×
      </button>
    </div>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast fora do ToastProvider');
  return ctx;
}
