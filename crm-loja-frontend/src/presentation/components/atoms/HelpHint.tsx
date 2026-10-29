import { useEffect, useId, useRef, useState } from 'react';

// O "?" de ajuda (padrão da Plataforma de Operações): uma frase curta, em português simples, ao lado do título.
// Abre ao passar o mouse, ao focar com o teclado ou ao tocar; fecha com Esc, clique fora ou tocando de novo.
export function HelpHint({ text, label = 'Ajuda' }: { text: string; label?: string }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const box = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    const onClick = (e: MouseEvent) => { if (box.current && !box.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClick);
    };
  }, [open]);

  return (
    <span ref={box} className="relative inline-flex align-middle">
      <button
        type="button"
        aria-label={label}
        aria-describedby={open ? id : undefined}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        className="flex size-[18px] items-center justify-center rounded-full border border-border text-[11px] font-semibold text-muted hover:border-primary hover:text-primary"
      >
        ?
      </button>
      {open && (
        <span
          id={id}
          role="tooltip"
          className="absolute top-full left-1/2 z-50 mt-2 w-60 max-w-[80vw] -translate-x-1/2 rounded-lg border border-border bg-surface px-3 py-2 text-xs font-normal text-text shadow-lg"
        >
          {text}
        </span>
      )}
    </span>
  );
}
