import type { Availability, Role, SaleStatus, StockMovementType } from './dto';

// Tradução em um lugar só: códigos da API → palavras da tela.

export const saleStatusLabel: Record<SaleStatus, string> = {
  DRAFT: 'Aberta',
  CONFIRMED: 'Confirmada',
  CANCELLED: 'Cancelada',
};

export const saleStatusTone: Record<SaleStatus, Tone> = {
  DRAFT: 'info',
  CONFIRMED: 'success',
  CANCELLED: 'error',
};

export const availabilityLabel: Record<Availability, string> = {
  AVAILABLE: 'Disponível',
  OUT_OF_STOCK: 'Sem estoque',
  INACTIVE: 'Inativo',
};

export const availabilityTone: Record<Availability, Tone> = {
  AVAILABLE: 'success',
  OUT_OF_STOCK: 'error',
  INACTIVE: 'neutral',
};

export const movementLabel: Record<StockMovementType, string> = {
  ENTRY: 'Entrada',
  ADJUSTMENT: 'Ajuste',
  SALE: 'Venda',
  SALE_CANCELLATION: 'Estorno de venda',
};

export const roleLabel: Record<Role, string> = {
  MANAGER: 'Gerente',
  SELLER: 'Vendedor(a)',
  STOCK_KEEPER: 'Estoquista',
};

export type Tone = 'neutral' | 'success' | 'warning' | 'error' | 'info' | 'primary';

export const customerLabel = (name: string | null) => name ?? 'Consumidor não identificado';
