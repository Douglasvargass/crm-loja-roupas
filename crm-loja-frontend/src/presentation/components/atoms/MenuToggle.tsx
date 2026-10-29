import { cn } from '@/lib/cn';

// Botão dos 3 traços. Com `morph`, os traços viram um X (menu do celular aberto). No computador ele
// continua com 3 traços: esconde e mostra o menu lateral.
export function MenuToggle({ expanded, morph, label, onClick, controls }: {
  expanded: boolean;
  morph: boolean;
  label: string;
  onClick: () => void;
  controls: string;
}) {
  const bar = 'absolute left-2.5 block h-0.5 w-5 rounded-full bg-current transition-all duration-200 motion-reduce:transition-none';
  const open = morph && expanded;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      aria-expanded={expanded}
      aria-controls={controls}
      className="relative size-10 shrink-0 rounded-lg text-text hover:bg-surface-2 focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:outline-none"
    >
      <span className={cn(bar, open ? 'top-[19px] rotate-45' : 'top-[12px]')} />
      <span className={cn(bar, 'top-[19px]', open && 'opacity-0')} />
      <span className={cn(bar, open ? 'top-[19px] -rotate-45' : 'top-[26px]')} />
    </button>
  );
}
