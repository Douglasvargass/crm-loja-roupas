import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { http } from './http';
import { qk } from './queryKeys';
import type {
  AvailabilityFilter,
  CategoryDto,
  CategoryInput,
  CustomerDetailDto,
  CustomerDto,
  CustomerInput,
  DashboardDto,
  MeDto,
  Page,
  ProductDetailDto,
  ProductDto,
  ProductInput,
  Role,
  SaleDto,
  SaleStatus,
  SaleSummaryDto,
  StockAlertsDto,
  StockMovementDto,
  StockMovementType,
  UserRowDto,
} from './dto';

// Hooks de dados: as telas nunca chamam http direto.

function useInvalidate() {
  const qc = useQueryClient();
  return (...keys: QueryKey[]) => Promise.all(keys.map((queryKey) => qc.invalidateQueries({ queryKey })));
}

// Tudo que mexe em estoque derruba o cache destes (CLAUDE.md do front).
const STOCK_SENSITIVE = [qk.dashboard, qk.products, qk.stockAlerts, qk.stockMovements, qk.sales, qk.customers];

/* ---------- Acesso ---------- */

export const authApi = {
  me: () => http.get<MeDto>('/auth/me'),
  login: (login: string, password: string) => http.post<MeDto>('/auth/login', { login, password }),
  logout: () => http.post<void>('/auth/logout'),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post<MeDto>('/auth/change-password', { currentPassword, newPassword }),
};

/* ---------- Clientes ---------- */

export function useCustomers(params: { search?: string; active?: boolean; page: number; size?: number }) {
  return useQuery({
    queryKey: [...qk.customers, params],
    queryFn: () => http.get<Page<CustomerDto>>('/crm/customers', { ...params, size: params.size ?? 10 }),
    placeholderData: keepPreviousData,
  });
}

export function useCustomer(id: number) {
  return useQuery({
    queryKey: qk.customer(id),
    queryFn: () => http.get<CustomerDetailDto>(`/crm/customers/${id}`),
    enabled: id > 0,
  });
}

export function useCustomerSales(id: number) {
  return useQuery({
    queryKey: [...qk.customer(id), 'sales'],
    queryFn: () => http.get<SaleSummaryDto[]>(`/crm/customers/${id}/sales`),
  });
}

export function useSaveCustomer() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: CustomerInput }) =>
      id ? http.put<CustomerDto>(`/crm/customers/${id}`, input) : http.post<CustomerDto>('/crm/customers', input),
    onSuccess: () => invalidate(qk.customers, qk.dashboard),
  });
}

export function useCustomerActions() {
  const invalidate = useInvalidate();
  const done = () => invalidate(qk.customers, qk.dashboard);
  return {
    remove: useMutation({ mutationFn: (id: number) => http.del<void>(`/crm/customers/${id}`), onSuccess: done }),
    setActive: useMutation({
      mutationFn: ({ id, active }: { id: number; active: boolean }) =>
        http.patch<CustomerDto>(`/crm/customers/${id}/${active ? 'activate' : 'deactivate'}`),
      onSuccess: done,
    }),
  };
}

/* ---------- Categorias ---------- */

export function useCategories() {
  return useQuery({ queryKey: qk.categories, queryFn: () => http.get<CategoryDto[]>('/crm/categories') });
}

export function useSaveCategory() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: CategoryInput }) =>
      id ? http.put<CategoryDto>(`/crm/categories/${id}`, input) : http.post<CategoryDto>('/crm/categories', input),
    onSuccess: () => invalidate(qk.categories, qk.products),
  });
}

export function useDeleteCategory() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: number) => http.del<void>(`/crm/categories/${id}`),
    onSuccess: () => invalidate(qk.categories),
  });
}

/* ---------- Produtos ---------- */

export interface ProductFilters {
  name?: string;
  categoryId?: number;
  size?: string;
  code?: string;
  availability?: AvailabilityFilter;
  page: number;
  pageSize?: number;
}

export function useProducts(filters: ProductFilters, enabled = true) {
  return useQuery({
    queryKey: [...qk.products, filters],
    queryFn: () => http.get<Page<ProductDto>>('/crm/products', { ...filters }),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useProduct(id: number) {
  return useQuery({ queryKey: qk.product(id), queryFn: () => http.get<ProductDetailDto>(`/crm/products/${id}`) });
}

export function useSaveProduct() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: ProductInput }) =>
      id ? http.put<ProductDto>(`/crm/products/${id}`, input) : http.post<ProductDto>('/crm/products', input),
    onSuccess: () => invalidate(...STOCK_SENSITIVE, qk.categories),
  });
}

export function useProductActions() {
  const invalidate = useInvalidate();
  const done = () => invalidate(...STOCK_SENSITIVE, qk.categories);
  return {
    remove: useMutation({ mutationFn: (id: number) => http.del<void>(`/crm/products/${id}`), onSuccess: done }),
    setActive: useMutation({
      mutationFn: ({ id, active }: { id: number; active: boolean }) =>
        http.patch<ProductDto>(`/crm/products/${id}/${active ? 'activate' : 'deactivate'}`),
      onSuccess: done,
    }),
  };
}

/* ---------- Estoque ---------- */

export interface MovementFilters {
  productId?: number;
  type?: StockMovementType;
  from?: string;
  to?: string;
  page: number;
}

export function useStockMovements(filters: MovementFilters) {
  return useQuery({
    queryKey: [...qk.stockMovements, filters],
    queryFn: () => http.get<Page<StockMovementDto>>('/crm/stock/movements', { ...filters, size: 15 }),
    placeholderData: keepPreviousData,
  });
}

export function useStockAlerts() {
  return useQuery({ queryKey: qk.stockAlerts, queryFn: () => http.get<StockAlertsDto>('/crm/stock/alerts') });
}

export function useStockOperation() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (p: { kind: 'entry' | 'adjustment'; productId: number; quantity: number; reason: string }) =>
      http.post<StockMovementDto>(p.kind === 'entry' ? '/crm/stock/entries' : '/crm/stock/adjustments', {
        productId: p.productId,
        quantity: p.quantity,
        reason: p.reason,
      }),
    onSuccess: () => invalidate(...STOCK_SENSITIVE),
  });
}

/* ---------- Vendas ---------- */

export interface SaleFilters {
  id?: number;
  customerId?: number;
  from?: string;
  to?: string;
  status?: SaleStatus;
  page: number;
}

export function useSales(filters: SaleFilters) {
  return useQuery({
    queryKey: [...qk.sales, filters],
    queryFn: () => http.get<Page<SaleSummaryDto>>('/crm/sales', { ...filters }),
    placeholderData: keepPreviousData,
  });
}

export function useSale(id: number) {
  return useQuery({ queryKey: qk.sale(id), queryFn: () => http.get<SaleDto>(`/crm/sales/${id}`) });
}

export function useSaleActions(saleId?: number) {
  const qc = useQueryClient();
  const invalidate = useInvalidate();
  const put = (sale: SaleDto) => qc.setQueryData(qk.sale(sale.id), sale);
  return {
    open: useMutation({
      mutationFn: (customerId: number | null) => http.post<SaleDto>('/crm/sales', { customerId }),
      onSuccess: (s) => {
        put(s);
        return invalidate(qk.sales);
      },
    }),
    addItem: useMutation({
      mutationFn: (p: { productId: number; quantity: number }) => http.post<SaleDto>(`/crm/sales/${saleId}/items`, p),
      onSuccess: put,
    }),
    updateItem: useMutation({
      mutationFn: (p: { itemId: number; quantity: number }) =>
        http.put<SaleDto>(`/crm/sales/${saleId}/items/${p.itemId}`, { quantity: p.quantity }),
      onSuccess: put,
    }),
    removeItem: useMutation({
      mutationFn: (itemId: number) => http.del<SaleDto>(`/crm/sales/${saleId}/items/${itemId}`),
      onSuccess: put,
    }),
    confirm: useMutation({
      mutationFn: () => http.post<SaleDto>(`/crm/sales/${saleId}/confirm`),
      onSuccess: (s) => {
        put(s);
        return invalidate(...STOCK_SENSITIVE);
      },
    }),
    cancel: useMutation({
      mutationFn: (reason: string) => http.post<SaleDto>(`/crm/sales/${saleId}/cancel`, { reason }),
      onSuccess: (s) => {
        put(s);
        return invalidate(...STOCK_SENSITIVE);
      },
    }),
  };
}

/* ---------- Dashboard ---------- */

export function useDashboard(from: string, to: string) {
  return useQuery({
    queryKey: [...qk.dashboard, from, to],
    queryFn: () => http.get<DashboardDto>('/crm/dashboard', { from, to }),
    placeholderData: keepPreviousData,
  });
}

/* ---------- Usuários ---------- */

export function useUsers() {
  return useQuery({ queryKey: qk.users, queryFn: () => http.get<UserRowDto[]>('/admin/users') });
}

export function useUserActions() {
  const invalidate = useInvalidate();
  const done = () => invalidate(qk.users);
  return {
    create: useMutation({
      mutationFn: (p: { login: string; fullName: string; role: Role }) =>
        http.post<{ user: UserRowDto; provisionalPassword: string }>('/admin/users', p),
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: (p: { id: number; role?: Role; active?: boolean }) =>
        http.patch<UserRowDto>(`/admin/users/${p.id}`, { role: p.role, active: p.active }),
      onSuccess: done,
    }),
  };
}
