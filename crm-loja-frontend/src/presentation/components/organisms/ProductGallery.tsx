import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/app/context/ToastContext';
import type { ProductImageDto, ProductImageView } from '@/data/dto';
import { useProductImageActions, useProductImages } from '@/data/hooks';
import { mediaUrl } from '@/data/http';
import { cn } from '@/lib/cn';
import { Button } from '../atoms/Button';
import { Icon } from '../atoms/Icon';
import { Skeleton } from '../atoms/Skeleton';

const VIEW_LABEL: Record<ProductImageView, string> = {
  FRONT: 'Frente', BACK: 'Costas', SIDE: 'Lateral', DETAIL: 'Detalhe', OTHER: 'Outra',
};

// Legenda: tipo, autoria e licença (sem repetir "Ilustração" quando a fonte já começa assim).
function caption(i: ProductImageDto) {
  const kind = i.illustration ? 'Ilustração' : 'Foto';
  return `${i.source.startsWith(kind) ? '' : `${kind} · `}${i.source} · ${i.license}`;
}

// Galeria do produto: foto grande, miniaturas e "Ampliar" (tela cheia com setas, teclado e toque).
// Com `editable`, o gerente envia, troca a capa e exclui fotos.
export function ProductGallery({ productId, name, editable = false }: { productId: number; name: string; editable?: boolean }) {
  const query = useProductImages(productId);
  const images = query.data ?? [];
  const [index, setIndex] = useState(0);
  const [zoom, setZoom] = useState(false);
  const current = images[Math.min(index, Math.max(images.length - 1, 0))];

  useEffect(() => { if (index >= images.length && images.length > 0) setIndex(0); }, [images.length, index]);

  if (query.isPending) return <Skeleton className="aspect-square w-full" />;

  return (
    <div className="flex flex-col gap-3">
      {current ? (
        <button type="button" onClick={() => setZoom(true)} className="group relative overflow-hidden rounded-xl border border-border bg-surface-2"
          aria-label={`Ampliar a foto ${VIEW_LABEL[current.view]} de ${name}`}>
          <img src={mediaUrl(current.url)} alt={`${name}: ${VIEW_LABEL[current.view]}`} className="product-photo aspect-square w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]" />
          <span className="absolute top-2 left-2 rounded-full bg-surface/90 px-2 py-0.5 text-xs font-medium">{VIEW_LABEL[current.view]}</span>
          <span className="absolute right-2 bottom-2 flex items-center gap-1 rounded-full bg-surface/90 px-2 py-1 text-xs font-medium">
            <Icon name="search" className="size-3.5" /> Ampliar
          </span>
        </button>
      ) : (
        <div className="grid aspect-square w-full place-items-center rounded-xl border border-dashed border-border bg-surface-2 text-sm text-muted">
          Sem fotos ainda{editable ? ': envie a primeira abaixo.' : '.'}
        </div>
      )}

      {images.length > 1 && (
        <div className="grid grid-cols-4 gap-2">
          {images.map((img, i) => (
            <button key={img.id} type="button" onClick={() => setIndex(i)} aria-label={`Ver ${VIEW_LABEL[img.view]}`} aria-pressed={i === index}
              className={cn('overflow-hidden rounded-lg border-2 bg-surface-2', i === index ? 'border-primary' : 'border-transparent hover:border-border')}>
              <img src={mediaUrl(img.url)} alt="" loading="lazy" className="product-photo aspect-square w-full object-cover" />
            </button>
          ))}
        </div>
      )}

      {current && (
        <p className="text-xs text-muted">
          {caption(current)}
        </p>
      )}

      {editable && <GalleryEditor productId={productId} images={images} current={current} />}
      {zoom && images.length > 0 && <Lightbox images={images} start={index} name={name} onClose={(i) => { setIndex(i); setZoom(false); }} />}
    </div>
  );
}

function GalleryEditor({ productId, images, current }: { productId: number; images: ProductImageDto[]; current?: ProductImageDto }) {
  const { upload, remove, cover } = useProductImageActions(productId);
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [view, setView] = useState<ProductImageView>('FRONT');
  const full = images.length >= 8;

  async function send(file: File | undefined) {
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      toast.error('A imagem pode ter no máximo 2 MB.');
      return;
    }
    try {
      await upload.mutateAsync({ file, view });
      toast.success('Foto enviada para a galeria.');
    } catch (err) {
      toast.error(err);
    } finally {
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
      <select aria-label="Vista da nova foto" value={view} onChange={(e) => setView(e.target.value as ProductImageView)}
        className="h-9 rounded-md border border-border bg-surface px-2 text-sm">
        {(Object.keys(VIEW_LABEL) as ProductImageView[]).map((v) => <option key={v} value={v}>{VIEW_LABEL[v]}</option>)}
      </select>
      <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={(e) => void send(e.target.files?.[0])} />
      <Button size="sm" variant="secondary" loading={upload.isPending} disabled={full} title={full ? 'Cada produto pode ter até 8 fotos.' : undefined}
        onClick={() => fileRef.current?.click()}>
        Enviar foto
      </Button>
      {current && current.position > 0 && (
        <Button size="sm" variant="ghost" loading={cover.isPending}
          onClick={async () => { try { await cover.mutateAsync(current.id); toast.success('Essa foto agora é a capa.'); } catch (err) { toast.error(err); } }}>
          Usar como capa
        </Button>
      )}
      {current && (
        <Button size="sm" variant="ghost" loading={remove.isPending}
          onClick={async () => { try { await remove.mutateAsync(current.id); toast.success('Foto removida.'); } catch (err) { toast.error(err); } }}>
          Excluir esta foto
        </Button>
      )}
      <span className="w-full text-xs text-muted">JPG, PNG ou WEBP, até 2 MB, no máximo 8 fotos.</span>
    </div>
  );
}

// Tela cheia: setas, teclado (← → Esc) e deslizar no celular.
export function Lightbox({ images, start, name, onClose }: { images: ProductImageDto[]; start: number; name: string; onClose: (index: number) => void }) {
  const [i, setI] = useState(start);
  const touch = useRef<number | null>(null);
  const go = useCallback((d: number) => setI((x) => (x + d + images.length) % images.length), [images.length]);
  const img = images[i]!;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose(i);
      if (e.key === 'ArrowRight') go(1);
      if (e.key === 'ArrowLeft') go(-1);
    };
    window.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prev; };
  }, [go, i, onClose]);

  return (
    <div role="dialog" aria-modal="true" aria-label={`Fotos de ${name}`} className="fixed inset-0 z-[60] flex flex-col bg-black/90 text-white"
      onTouchStart={(e) => { touch.current = e.touches[0]?.clientX ?? null; }}
      onTouchEnd={(e) => {
        const start = touch.current;
        const end = e.changedTouches[0]?.clientX;
        if (start != null && end != null && Math.abs(end - start) > 40) go(end < start ? 1 : -1);
        touch.current = null;
      }}>
      <div className="flex items-center justify-between gap-3 px-4 py-3">
        <span className="min-w-0 truncate text-sm">{name} · <strong>{VIEW_LABEL[img.view]}</strong> · {i + 1} de {images.length}</span>
        <button type="button" onClick={() => onClose(i)} aria-label="Fechar fotos" className="rounded-lg p-2 hover:bg-white/10">
          <Icon name="close" className="size-6" />
        </button>
      </div>
      <div className="relative flex min-h-0 flex-1 items-center justify-center px-2 sm:px-16">
        <img src={mediaUrl(img.url)} alt={`${name}: ${VIEW_LABEL[img.view]}`} className="product-photo max-h-full max-w-full rounded-lg object-contain" />
        {images.length > 1 && (
          <>
            <button type="button" onClick={() => go(-1)} aria-label="Foto anterior" className="absolute left-2 rounded-full bg-white/15 p-3 text-2xl leading-none hover:bg-white/25 sm:left-4">‹</button>
            <button type="button" onClick={() => go(1)} aria-label="Próxima foto" className="absolute right-2 rounded-full bg-white/15 p-3 text-2xl leading-none hover:bg-white/25 sm:right-4">›</button>
          </>
        )}
      </div>
      <div className="flex justify-center gap-2 overflow-x-auto px-4 py-3">
        {images.map((m, k) => (
          <button key={m.id} type="button" onClick={() => setI(k)} aria-label={`Ver ${VIEW_LABEL[m.view]}`}
            className={cn('size-14 shrink-0 overflow-hidden rounded-md border-2', k === i ? 'border-white' : 'border-transparent opacity-60 hover:opacity-100')}>
            <img src={mediaUrl(m.url)} alt="" className="product-photo size-full object-cover" />
          </button>
        ))}
      </div>
      <p className="pb-3 text-center text-xs text-white/60">{caption(img)}</p>
    </div>
  );
}
