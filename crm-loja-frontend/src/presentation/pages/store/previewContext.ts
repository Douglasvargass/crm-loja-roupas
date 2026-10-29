import { createContext, useContext } from 'react';
import type { ShopProductDto } from '@/data/dto';

// Estado da "Prévia da loja" (StorePreview.tsx). Fica num arquivo separado porque o StoreShell e as telas da loja
// precisam saber se estão na prévia, e o StorePreview usa o StoreShell: assim nenhum arquivo importa o outro em círculo.

export interface PreviewItem { product: ShopProductDto; quantity: number }

export interface Preview {
  base: string;
  items: PreviewItem[];
  count: number;
  total: number;
  add: (p: ShopProductDto, qty: number) => void;
  change: (productId: number, qty: number) => void;
  remove: (productId: number) => void;
}

export const PreviewContext = createContext<Preview | null>(null);

// null = loja de verdade (cliente logado); objeto = prévia da equipe.
export function useStorePreview() {
  return useContext(PreviewContext);
}
