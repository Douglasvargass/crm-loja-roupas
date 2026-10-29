import { cn } from '@/lib/cn';

// Bloco cinza pulsando no lugar do conteúdo enquanto os dados carregam (evita a tela "pular" quando chegam).
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-surface-2', className)} aria-hidden />;
}
