import type { Availability, CoverageStatus, PurchaseOrderStatus, Role, SaleStatus, StockMovementType, StockRequestStatus, TargetStatus } from './dto';

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
  PURCHASING: 'Operações e Compras',
  CUSTOMER: 'Cliente',
  DEVELOPER: 'Engenheiro(a) de Software',
  MARKETING_DEV: 'Marketing e Desenvolvimento',
};

export type Tone = 'neutral' | 'success' | 'warning' | 'error' | 'info' | 'primary';

export const customerLabel = (name: string | null) => name ?? 'Consumidor não identificado';

export const channelLabel = { STORE: 'Loja física', ONLINE: 'Online' } as const;
export const paymentLabel = { PIX: 'Pix', CARD: 'Cartão de crédito' } as const;

export const requestStatusLabel: Record<StockRequestStatus, string> = {
  OPEN: 'Aberto',
  RESOLVED: 'Resolvido',
  CANCELLED: 'Cancelado',
};

export const requestStatusTone: Record<StockRequestStatus, Tone> = {
  OPEN: 'warning',
  RESOLVED: 'success',
  CANCELLED: 'neutral',
};

// Situação da meta (spec crm/06). "No ritmo" = a projeção alcança a meta; "Atenção" = projeção entre 80% e 100%.
export const targetStatusLabel: Record<TargetStatus, string> = {
  NO_TARGET: 'Sem meta',
  NOT_STARTED: 'Ainda não começou',
  ACHIEVED: 'Meta batida',
  ON_TRACK: 'No ritmo',
  ATTENTION: 'Atenção',
  BEHIND: 'Abaixo do ritmo',
  MISSED: 'Não bateu',
};

export const targetStatusTone: Record<TargetStatus, Tone> = {
  NO_TARGET: 'neutral',
  NOT_STARTED: 'neutral',
  ACHIEVED: 'success',
  ON_TRACK: 'success',
  ATTENTION: 'warning',
  BEHIND: 'error',
  MISSED: 'error',
};

// Reposição (spec crm/11): situação de cada peça no painel "O que vai faltar".
export const coverageLabel: Record<CoverageStatus, string> = {
  OUT: 'Zerado',
  CRITICAL: 'Acaba em até 7 dias',
  ATTENTION: 'Acaba em até 21 dias',
  OK: 'Tranquilo',
  STALE: 'Parado',
  NEW: 'Novo, sem histórico',
};

export const coverageTone: Record<CoverageStatus, Tone> = {
  OUT: 'error',
  CRITICAL: 'error',
  ATTENTION: 'warning',
  OK: 'success',
  STALE: 'neutral',
  NEW: 'info',
};

export const purchaseStatusLabel: Record<PurchaseOrderStatus, string> = {
  OPEN: 'Pedido feito',
  IN_TRANSIT: 'Em trânsito',
  CHECKING: 'Em conferência',
  RECEIVED: 'Entrada feita',
  CANCELLED: 'Cancelado',
};

export const purchaseStatusTone: Record<PurchaseOrderStatus, Tone> = {
  OPEN: 'neutral',
  IN_TRANSIT: 'info',
  CHECKING: 'warning',
  RECEIVED: 'success',
  CANCELLED: 'neutral',
};
