import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

// HU31: todo resultado aparece num aviso verde (sucesso) ou vermelho (erro) que some em 4 s.
type Toast = { id: number; kind: 'success' | 'error'; text: string };

interface ToastValue {
  success: (text: string) => void;
  error: (err: unknown) => void;
}

const ToastContext = createContext<ToastValue | null>(null);
let seq = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((kind: Toast['kind'], text: string) => {
    const id = ++seq;
    setToasts((t) => [...t, { id, kind, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4000);
  }, []);

  const success = useCallback((text: string) => push('success', text), [push]);
  const error = useCallback(
    (err: unknown) => push('error', err instanceof Error ? err.message : 'Algo deu errado. Tente de novo.'),
    [push],
  );

  return (
    <ToastContext.Provider value={{ success, error }}>
      {children}
      <div className="pointer-events-none fixed right-4 bottom-4 left-4 z-50 flex flex-col items-end gap-2 sm:left-auto">
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.kind === 'error' ? 'alert' : 'status'}
            className={cn(
              'pointer-events-auto w-full max-w-sm rounded-lg border px-4 py-3 text-sm font-medium shadow-lg',
              t.kind === 'success'
                ? 'border-success/30 bg-success-soft text-success'
                : 'border-error/30 bg-error-soft text-error',
            )}
          >
            {t.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast fora do ToastProvider');
  return ctx;
}
