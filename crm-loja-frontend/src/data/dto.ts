// Tipos 1:1 com a API (inglês). Mudou um DTO no back-end → mudar aqui e no adapters.ts.

// DEVELOPER e MARKETING_DEV são a equipe técnica: na permissão valem por gerente (ver domain/permissions.ts).
// PURCHASING (Operações e Compras) vale por estoquista e recebe os chamados de peça em falta.
// CUSTOMER = cliente do espaço do cliente (módulo loja, spec crm/07): não tem papel no crm.
export type Role = 'MANAGER' | 'SELLER' | 'STOCK_KEEPER' | 'PURCHASING' | 'DEVELOPER' | 'MARKETING_DEV' | 'CUSTOMER';
export type SaleChannel = 'STORE' | 'ONLINE';
export type SaleStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';

// Spec crm/06 (metas e painel do vendedor). Espelho de TargetDtos.java.
export type TargetStatus = 'NO_TARGET' | 'NOT_STARTED' | 'ACHIEVED' | 'ON_TRACK' | 'ATTENTION' | 'BEHIND' | 'MISSED';

export interface SellerPanelDto {
  month: string;              // aaaa-mm
  closed: boolean;
  sellerId: number;
  sellerName: string;
  target: number | null;      // null = meta ainda não definida
  revenue: number;
  confirmedSales: number;
  averageTicket: number;
  cancelledSales: number;
  percent: number | null;
  remaining: number | null;
  perBusinessDay: number;
  projection: number;
  neededPerDay: number | null;
  businessDaysInMonth: number;
  businessDaysElapsed: number;
  businessDaysLeft: number;
  status: TargetStatus;
  today: { sales: number; revenue: number } | null;
  revenueByDay: { date: string; sales: number; revenue: number }[];
  topProducts: { productId: number; code: string; name: string; quantity: number; revenue: number }[];
  recentSales: { id: number; status: SaleStatus; createdAt: string; confirmedAt: string | null; customerName: string | null; total: number }[];
}

// Spec crm/09: chamado de peça em falta.
export type StockRequestStatus = 'OPEN' | 'RESOLVED' | 'CANCELLED';

export interface StockRequestDto {
  id: number;
  status: StockRequestStatus;
  description: string;
  size: string | null;
  color: string | null;
  quantity: number;
  productId: number | null;
  productCode: string | null;
  productName: string | null;
  productStock: number | null;
  customerId: number | null;
  customerName: string | null;
  openedVia: 'SCREEN' | 'ASSISTANT';
  openedById: number | null;
  openedByName: string | null;
  createdAt: string;
  closingNote: string | null;
  closedByName: string | null;
  closedAt: string | null;
}

export interface StockRequestInput {
  productId?: number;
  description: string;
  size?: string;
  color?: string;
  quantity: number;
  customerId?: number;
}

export interface RequestAlertsDto {
  kind: 'OPEN_REQUESTS' | 'MY_UPDATES';
  count: number;
  items: StockRequestDto[];
}

// Spec crm/08: diário do agente de vendas da demonstração.
export interface DemoAgentDayDto {
  day: string;
  status: 'GENERATED' | 'SKIPPED';
  source: 'AI' | 'DEFAULT' | 'NONE';
  demandFactor: number | null;
  highlightFamily: string | null;
  highlightFactor: number | null;
  reason: string;
  salesCreated: number;
  revenue: number;
}

export interface DemoAgentDiaryDto {
  enabled: boolean;
  aiEnabled: boolean;
  days: DemoAgentDayDto[];
}

export interface TargetBoardRowDto {
  sellerId: number;
  sellerName: string;
  target: number | null;
  revenue: number;
  confirmedSales: number;
  percent: number | null;
  projection: number;
  remaining: number | null;
  status: TargetStatus;
}

export interface TargetBoardDto {
  month: string;
  closed: boolean;
  editable: boolean;
  businessDaysInMonth: number;
  businessDaysElapsed: number;
  businessDaysLeft: number;
  totalTarget: number;
  totalRevenue: number;
  rows: TargetBoardRowDto[];
}
export type Availability = 'AVAILABLE' | 'OUT_OF_STOCK' | 'INACTIVE';
export type AvailabilityFilter = 'ALL' | 'AVAILABLE' | 'OUT_OF_STOCK' | 'LOW_STOCK' | 'INACTIVE';
export type StockMovementType = 'ENTRY' | 'ADJUSTMENT' | 'SALE' | 'SALE_CANCELLATION';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MeDto {
  id: number;
  login: string;
  fullName: string;
  superAdmin: boolean;
  mustChangePassword: boolean;
  role: Role | null;
  // "Ver como" (spec crm/10): preenchido quando o gerente está vendo o sistema como esta pessoa; só leitura.
  viewAs: { viewerId: number; viewerName: string } | null;
}

export interface CustomerDto {
  id: number;
  fullName: string;
  cpf: string;
  phone: string;
  email: string;
  zipCode: string;
  street: string;
  number: string;
  complement: string | null;
  district: string;
  city: string;
  state: string;
  active: boolean;
  createdAt: string;
}

export type CustomerInput = Omit<CustomerDto, 'id' | 'active' | 'createdAt'>;

export interface CustomerDetailDto {
  customer: CustomerDto;
  confirmedPurchases: number;
  confirmedTotal: number;
  lastPurchaseAt: string | null;
  userId: number | null;   // conta de acesso à loja online, se houver
}

export interface CategoryDto {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  productCount: number;
}

export interface CategoryInput {
  name: string;
  description: string | null;
  active: boolean;
}

export interface ProductDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  categoryId: number;
  categoryName: string;
  size: string;
  color: string;
  price: number;
  stockQty: number;
  minStock: number;
  active: boolean;
  availability: Availability;
  lowStock: boolean;
}

export interface ProductInput {
  code: string;
  name: string;
  description: string | null;
  categoryId: number;
  size: string;
  color: string;
  price: number;
  initialStock: number;
  minStock: number;
}

export interface StockMovementDto {
  id: number;
  createdAt: string;
  productId: number;
  productCode: string;
  productName: string;
  type: StockMovementType;
  quantity: number;
  balanceAfter: number;
  saleId: number | null;
  reason: string | null;
}

export interface ProductDetailDto {
  product: ProductDto;
  recentMovements: StockMovementDto[];
  totalSold: number;
}

export interface StockAlertsDto {
  lowStock: ProductDto[];
  outOfStock: ProductDto[];
}

export interface SaleSummaryDto {
  id: number;
  createdAt: string;
  confirmedAt: string | null;
  customerId: number | null;
  customerName: string | null;
  itemsQuantity: number;
  total: number;
  status: SaleStatus;
  channel: SaleChannel;
}

export interface SaleItemDto {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  availableStock: number;
}

export interface SaleDto {
  id: number;
  status: SaleStatus;
  customerId: number | null;
  customerName: string | null;
  customerCpf: string | null;
  total: number;
  createdAt: string;
  confirmedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  items: SaleItemDto[];
  channel: SaleChannel;
}

// ---------------------------------------------------------------- espaço do cliente (spec crm/07)
export interface ShopProductDto {
  id: number; name: string; description: string | null; category: string; size: string; color: string;
  price: number; lastUnits: boolean;
}
export interface CartItemDto {
  itemId: number; productId: number; code: string; name: string; unitPrice: number; quantity: number;
  subtotal: number; available: boolean;
}
export interface CartDto { saleId: number | null; items: CartItemDto[]; total: number; count: number }
export interface OrderDto {
  id: number; status: SaleStatus; channel: SaleChannel; createdAt: string; confirmedAt: string | null; total: number;
  items: number; paymentMethod: 'PIX' | 'CARD' | null; paymentStatus: 'APPROVED' | 'REFUNDED' | null; cardLast4: string | null;
}
export interface OrderDetailDto {
  sale: SaleDto; paymentMethod: 'PIX' | 'CARD' | null; paymentStatus: 'APPROVED' | 'REFUNDED' | null; cardLast4: string | null;
}
export interface MyDataInput {
  phone: string; email: string; zipCode: string; street: string; number: string; complement: string | null;
  district: string; city: string; state: string;
}

export interface DashboardDto {
  totalCustomers: number;
  totalProducts: number;
  period: { from: string; to: string };
  selected: {
    categoryId: number | null; categoryName: string | null;
    productId: number | null; productName: string | null;
    day: string | null;
    sellerId: number | null; sellerName: string | null;
  };
  confirmedSales: number;
  revenue: number;
  averageTicket: number;
  cancelledSales: number;
  previousPeriod: { from: string; to: string; confirmedSales: number; revenue: number };
  topProducts: { productId: number; code: string; name: string; quantity: number; revenue: number }[];
  revenueByDay: { date: string; sales: number; revenue: number }[];
  salesByCategory: { categoryId: number; category: string; quantity: number; revenue: number }[];
  lowStock: StockAlertDto[];
  outOfStock: StockAlertDto[];
  // Opções do filtro de vendedor: contas ativas com papel de vendedor.
  sellers: { id: number; fullName: string }[];
}

export interface StockAlertDto {
  productId: number;
  code: string;
  name: string;
  stockQty: number;
  minStock: number;
}

export interface UserRowDto {
  id: number;
  login: string;
  fullName: string;
  role: Role;
  active: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
}

/* ---------- Reposição e pedidos de compra (spec crm/11) ---------- */

// OUT zerado · CRITICAL acaba em até 7 dias ou está no mínimo · ATTENTION até 21 dias · OK · STALE parado (sem venda em 30 dias)
// NEW = cadastrada há menos de 30 dias e ainda sem venda: cedo para julgar
export type CoverageStatus = 'OUT' | 'CRITICAL' | 'ATTENTION' | 'OK' | 'STALE' | 'NEW';

export interface PlanningItemDto {
  productId: number;
  code: string;
  name: string;
  size: string;
  color: string;
  categoryName: string;
  price: number;
  stock: number;
  minStock: number;
  soldInWindow: number;
  dailyAvg: number;
  daysLeft: number | null;
  runOutDate: string | null;
  incoming: number;
  nextArrival: string | null;
  openRequestsQty: number;
  suggestedQty: number;
  status: CoverageStatus;
  needsOrder: boolean;
  covered: boolean;
  arrivesAfterRunOut: boolean;
  lastSaleAt: string | null;
}

export interface PlanningSummaryDto {
  outOfStock: number;
  critical: number;
  attention: number;
  toOrder: number;
  openRequests: number;
  openOrders: number;
  ordered: number;
  inTransit: number;
  checking: number;
  lateOrders: number;
  arrivingThisWeek: number;
  receivedThisWeek: number;
  stale: number;
  staleValue: number;
}

export interface PlanningDto {
  today: string;
  windowDays: number;
  horizonDays: number;
  summary: PlanningSummaryDto;
  items: PlanningItemDto[];
}

// OPEN pedido feito · IN_TRANSIT saiu do fornecedor · CHECKING chegou e está em conferência · RECEIVED entrada feita
export type PurchaseOrderStatus = 'OPEN' | 'IN_TRANSIT' | 'CHECKING' | 'RECEIVED' | 'CANCELLED';

export interface PurchaseOrderItemDto {
  productId: number;
  productCode: string;
  productName: string;
  size: string;
  color: string;
  quantity: number;
  stockNow: number;
  receivedQuantity: number | null;   // contado na conferência
}

export interface PurchaseOrderDto {
  id: number;
  supplier: string;
  expectedDate: string;
  status: PurchaseOrderStatus;
  late: boolean;
  note: string | null;
  rescheduleCount: number;
  createdByName: string | null;
  createdAt: string;
  closingNote: string | null;
  closedByName: string | null;
  closedAt: string | null;
  invoiceNumber: string | null;
  shippedAt: string | null;
  arrivedAt: string | null;
  bonusInvoiceNumber: string | null;   // nota de bonificação do que veio a mais
  returnCount: number;                 // quantas vezes a nota voltou ao fornecedor
  totalUnits: number;
  items: PurchaseOrderItemDto[];
}

export interface PurchaseOrderInput {
  supplier: string;
  expectedDate: string;
  note?: string;
  items: { productId: number; quantity: number }[];
}

export interface PurchaseReceivedDto {
  order: PurchaseOrderDto;
  requestsResolved: number;
  bonusUnits: number;
}

// Spec crm/12: previsão de vendas (espelho de ForecastDtos.java).
export interface ForecastFactorDto { factor: number; learned: boolean }
export interface ForecastEventDto { name: string; date: string; rampDays: number; uplift: number; learned: boolean }
export interface ForecastModelDto {
  baselineRevenue: number;
  baselineSales: number;
  weekday: { day: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'; factor: number }[];
  payday: ForecastFactorDto;
  holiday: ForecastFactorDto;
  monthSeasonality: { active: boolean; factors: number[] | null };
  events: ForecastEventDto[];
  accuracy: { weeklyErrorPercent: number; weeks: number } | null;
}
export interface ForecastPeriodDto {
  key: string;
  label: string;
  from: string;
  to: string;
  revenue: number;
  low: number;
  high: number;
  sales: number;
  realized: number | null;
}
export interface ForecastDayDto {
  date: string;
  actual: number | null;
  revenue: number | null;
  low: number | null;
  high: number | null;
  sales: number | null;
  holiday: boolean;
  events: string[];
}
export interface SalesForecastDto {
  today: string;
  historyFrom: string;
  historyDays: number;
  model: ForecastModelDto;
  periods: ForecastPeriodDto[];
  days: ForecastDayDto[];
}
export interface TargetSuggestionRowDto {
  sellerId: number;
  sellerName: string;
  revenue: number;
  sharePercent: number;
  currentTarget: number | null;
  suggested: number | null;
  reason: string | null;
}
export interface TargetSuggestionsDto {
  month: string;
  monthForecast: number;
  growthPercent: number;
  basisFrom: string;
  basisTo: string;
  storeRevenue: number;
  rows: TargetSuggestionRowDto[];
}

// Galeria de imagens do produto (V13). url é relativa à API: use mediaUrl(url).
export type ProductImageView = 'FRONT' | 'BACK' | 'SIDE' | 'DETAIL' | 'OTHER';
export interface ProductImageDto {
  id: number;
  view: ProductImageView;
  position: number;
  url: string;
  source: string;
  license: string;
  illustration: boolean;
}
