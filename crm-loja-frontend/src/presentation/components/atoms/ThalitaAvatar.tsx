import { cn } from '@/lib/cn';

// Thalita em 3D, recortada da imagem oficial "De frente" (headset e camisa polo azul-marinho com a logo da
// Orden). Arquivo em public/thalita.jpg (256 × 256). A classe `thalita-photo` mantém a foto colorida mesmo no
// modo teste em preto e branco.
export function ThalitaAvatar({ className = 'size-10' }: { className?: string }) {
  return (
    <img
      src="/thalita.jpg"
      alt="Thalita, atendente virtual da Orden"
      width={256}
      height={256}
      draggable={false}
      className={cn('thalita-photo shrink-0 bg-surface-2 object-cover', className)}
    />
  );
}
