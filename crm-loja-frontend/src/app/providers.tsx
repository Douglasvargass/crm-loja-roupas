import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { SessionProvider } from './context/SessionContext';
import { ToastProvider } from './context/ToastContext';

// Envolve o app inteiro: cache de dados (TanStack Query), avisos na tela (toast) e a sessão de quem entrou.
export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          // Sem nova tentativa automática: erro de regra (409) ou de permissão (403) não melhora tentando de novo, e a
          // pessoa vê a mensagem na hora. 30 s de cache evitam buscar a mesma lista a cada troca de tela.
          queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: false },
          mutations: { retry: false },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SessionProvider>{children}</SessionProvider>
      </ToastProvider>
    </QueryClientProvider>
  );
}
