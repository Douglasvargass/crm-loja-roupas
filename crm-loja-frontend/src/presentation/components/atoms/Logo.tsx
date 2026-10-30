import { cn } from '@/lib/cn';

// Logo da Orden (PNG com fundo transparente). No tema escuro ela é marrom sobre café e some, então
// vai sobre uma placa creme (--logo-plate, transparente no tema claro).
export function Logo({ className = 'h-24', plate = true }: { className?: string; plate?: boolean }) {
  return (
    // A placa (--logo-plate) é transparente no tema claro e creme no escuro; sem ela a logo marrom some no fundo café.
    <span className={cn('inline-flex items-center rounded-xl bg-logo-plate', plate ? 'p-1.5' : 'p-0.5')}>
      <img src="/orden-logo.png" alt="Orden · moda masculina e feminina" className={cn('w-auto', className)} />
    </span>
  );
}
