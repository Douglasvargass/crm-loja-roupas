// Tipos 1:1 com a API (inglês). Mudou um DTO no back-end → mudar aqui e no adapters.ts.

export type Role = 'MANAGER' | 'SELLER' | 'STOCK_KEEPER';
export type SaleStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';
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
}

export interface DashboardDto {
  totalCustomers: number;
  totalProducts: number;
  period: { from: string; to: string };
  confirmedSales: number;
  revenue: number;
  averageTicket: number;
  cancelledSales: number;
  previousPeriod: { from: string; to: string; confirmedSales: number; revenue: number };
  topProducts: { productId: number; code: string; name: string; quantity: number; revenue: number }[];
  revenueByDay: { date: string; sales: number; revenue: number }[];
  salesByCategory: { category: string; quantity: number; revenue: number }[];
  lowStock: StockAlertDto[];
  outOfStock: StockAlertDto[];
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
