import { useState } from 'react';
import { productCoverUrl } from '@/data/http';
import { cn } from '@/lib/cn';
import { Icon } from './Icon';

// Capa do produto (a primeira imagem da galeria). Sem imagem, mostra um quadro neutro com o ícone de produto.
export function ProductPhoto({ productId, alt, className }: { productId: number; alt: string; className?: string }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <span className={cn('grid place-items-center bg-surface-2 text-muted', className)} role="img" aria-label={`${alt} (sem foto)`}>
        <Icon name="products" className="size-1/3 max-h-10 max-w-10" />
      </span>
    );
  }
  return (
    <img
      src={productCoverUrl(productId)}
      alt={alt}
      loading="lazy"
      onError={() => setFailed(true)}
      className={cn('product-photo bg-surface-2 object-cover', className)}
    />
  );
}
