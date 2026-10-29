import { cn } from '@/lib/cn';

// Logo da Orden (PNG com fundo transparente). No tema escuro ela é marrom sobre café e some, então
// vai sobre uma placa creme (--logo-plate, transparente no tema claro).
export function Logo({ className = 'h-24', plate = true }: { className?: string; plate?: boolean }) {
  return (
    <span className={cn('inline-flex items-center rounded-xl', plate && 'bg-logo-plate p-1.5')}>
      <img src="/orden-logo.png" alt="Orden · moda masculina e feminina" className={cn('w-auto', className)} />
    </span>
  );
}
