import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { http } from './http';
import { qk } from './queryKeys';
import type {
  SalesForecastDto,
  TargetSuggestionsDto,
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
  SellerPanelDto,
  DemoAgentDiaryDto,
  RequestAlertsDto,
  CartDto,
  MyDataInput,
  OrderDetailDto,
  OrderDto,
  ShopProductDto,
  StockRequestDto,
  StockRequestInput,
  StockRequestStatus,
  PlanningDto,
  PurchaseOrderDto,
  PurchaseOrderInput,
  PurchaseOrderStatus,
  PurchaseReceivedDto,
  TargetBoardDto,
  UserRowDto,
} from './dto';

// Hooks de dados: as telas nunca chamam http direto.

function useInvalidate() {
  const qc = useQueryClient();
  return (...keys: QueryKey[]) => Promise.all(keys.map((queryKey) => qc.invalidateQueries({ queryKey })));
}

// Tudo que mexe em estoque derruba o cache destes (CLAUDE.md do front). Venda também mexe no painel e nas metas.
const STOCK_SENSITIVE = [qk.dashboard, qk.products, qk.stockAlerts, qk.stockMovements, qk.sales, qk.customers, qk.myPanel, qk.targets, qk.requests, qk.planning, qk.purchaseOrders, qk.forecast, qk.targetSuggestions];

/* ---------- Acesso ---------- */

export const authApi = {
  me: () => http.get<MeDto>('/auth/me'),
  login: (login: string, password: string) => http.post<MeDto>('/auth/login', { login, password }),
  logout: () => http.post<void>('/auth/logout'),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post<MeDto>('/auth/change-password', { currentPassword, newPassword }),
  // "Ver como" (spec crm/10)
  viewAsTargets: () => http.get<UserRowDto[]>('/auth/view-as/targets'),
  viewAsStart: (userId: number) => http.post<void>('/auth/view-as', { userId }),
  viewAsStop: () => http.del<void>('/auth/view-as'),
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
  // id 0 = nenhum produto (ex.: novo chamado sem peça do catálogo): não consulta.
  return useQuery({ queryKey: qk.product(id), queryFn: () => http.get<ProductDetailDto>(`/crm/products/${id}`), enabled: id > 0 });
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

// Filtros dos cliques nos gráficos (todos opcionais). Clicar de novo no mesmo item remove o filtro.
// sellerId vem da lista "Vendedor(a)" acima dos cartões: vale para os cartões e os três gráficos.
export interface DashboardFilters {
  categoryId?: number;
  productId?: number;
  day?: string;
  sellerId?: number;
  // Canal (spec crm/07): STORE = loja física, ONLINE = espaço do cliente.
  channel?: 'STORE' | 'ONLINE';
}

export function useDashboard(from: string, to: string, filters: DashboardFilters = {}) {
  return useQuery({
    queryKey: [...qk.dashboard, from, to, filters],
    queryFn: () => http.get<DashboardDto>('/crm/dashboard', { from, to, ...filters }),
    placeholderData: keepPreviousData,
  });
}

/* ---------- Metas e painel do vendedor (spec crm/06) ---------- */

// month = aaaa-mm; vazio = mês atual (o servidor decide pelo fuso da loja).
export function useMyPanel(month?: string) {
  return useQuery({
    queryKey: [...qk.myPanel, month ?? ''],
    queryFn: () => http.get<SellerPanelDto>('/crm/my-panel', { month }),
    placeholderData: keepPreviousData,
  });
}

export function useTargets(month?: string) {
  return useQuery({
    queryKey: [...qk.targets, month ?? ''],
    queryFn: () => http.get<TargetBoardDto>('/crm/targets', { month }),
    placeholderData: keepPreviousData,
  });
}

export function useSetTarget() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (p: { sellerId: number; month: string; amount: number }) =>
      http.put<{ sellerId: number; month: string; amount: number }>(
        `/crm/targets/${p.sellerId}?month=${encodeURIComponent(p.month)}`, { amount: p.amount }),
    onSuccess: () => invalidate(qk.targets, qk.myPanel, qk.targetSuggestions),
  });
}

/* ---------- Espaço do cliente (spec crm/07) ---------- */

export const storeKeys = { all: ['store'] as const };

export function useShopProducts(f: { name?: string; categoryId?: number; size?: string; page: number }) {
  return useQuery({
    queryKey: [...storeKeys.all, 'products', f],
    queryFn: () => http.get<Page<ShopProductDto>>('/loja/products', f),
    placeholderData: keepPreviousData,
  });
}

export function useShopCategories() {
  return useQuery({ queryKey: [...storeKeys.all, 'categories'], queryFn: () => http.get<{ id: number; name: string }[]>('/loja/categories') });
}

// enabled = false na Prévia da loja: a equipe não tem carrinho no servidor (a prévia guarda o carrinho só na tela).
export function useCart(enabled = true) {
  return useQuery({ queryKey: [...storeKeys.all, 'cart'], queryFn: () => http.get<CartDto>('/loja/cart'), enabled });
}

export function useStoreActions() {
  const qc = useQueryClient();
  const done = () => qc.invalidateQueries({ queryKey: storeKeys.all });
  return {
    add: useMutation({ mutationFn: (p: { productId: number; quantity: number }) => http.post<CartDto>('/loja/cart/items', p), onSuccess: done }),
    change: useMutation({ mutationFn: (p: { itemId: number; quantity: number }) => http.put<CartDto>(`/loja/cart/items/${p.itemId}`, { quantity: p.quantity }), onSuccess: done }),
    remove: useMutation({ mutationFn: (itemId: number) => http.del<CartDto>(`/loja/cart/items/${itemId}`), onSuccess: done }),
    // Também em erro: o 409 "preço mudou" atualiza o carrinho no servidor, e a tela precisa mostrar o novo total.
    checkout: useMutation({ mutationFn: (p: { method: 'PIX' | 'CARD'; cardNumber?: string }) => http.post<OrderDetailDto>('/loja/checkout', p), onSettled: done }),
    saveMyData: useMutation({ mutationFn: (d: MyDataInput) => http.put<CustomerDto>('/loja/me', d), onSuccess: done }),
  };
}

export function useMyOrders() {
  return useQuery({ queryKey: [...storeKeys.all, 'orders'], queryFn: () => http.get<OrderDto[]>('/loja/orders') });
}

export function useMyOrder(id: number) {
  return useQuery({ queryKey: [...storeKeys.all, 'orders', id], queryFn: () => http.get<OrderDetailDto>(`/loja/orders/${id}`), enabled: id > 0 });
}

// Peças que o cliente pediu à loja (viram chamados de peça em falta, spec crm/09); enabled = false na Prévia da loja.
export function useMyPartRequests(enabled = true) {
  return useQuery({ queryKey: [...storeKeys.all, 'requests'], queryFn: () => http.get<Page<StockRequestDto>>('/loja/requests'), enabled });
}

export function useRequestPart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { description: string; size?: string; color?: string; quantity: number }) =>
      http.post<StockRequestDto>('/loja/requests', input),
    onSuccess: () => qc.invalidateQueries({ queryKey: storeKeys.all }),
  });
}

export function useMyData() {
  return useQuery({ queryKey: [...storeKeys.all, 'me'], queryFn: () => http.get<CustomerDto>('/loja/me') });
}

/* ---------- Chamados de peça em falta (spec crm/09) ---------- */

export function useRequests(status: StockRequestStatus | undefined, page: number) {
  return useQuery({
    queryKey: [...qk.requests, 'list', status ?? '', page],
    queryFn: () => http.get<Page<StockRequestDto>>('/crm/requests', { status, page, size: 20 }),
    placeholderData: keepPreviousData,
  });
}

// O sino consulta de minuto em minuto: o chamado aberto por outra pessoa aparece sem recarregar a página.
export function useRequestAlerts() {
  return useQuery({
    queryKey: [...qk.requests, 'alerts'],
    queryFn: () => http.get<RequestAlertsDto>('/crm/requests/alerts'),
    refetchInterval: 60_000,
  });
}

export function useRequestActions() {
  const invalidate = useInvalidate();
  const done = () => invalidate(qk.requests);
  return {
    open: useMutation({ mutationFn: (input: StockRequestInput) => http.post<StockRequestDto>('/crm/requests', input), onSuccess: done }),
    resolve: useMutation({
      mutationFn: (p: { id: number; note: string }) => http.post<StockRequestDto>(`/crm/requests/${p.id}/resolve`, { note: p.note }),
      onSuccess: done,
    }),
    cancel: useMutation({
      mutationFn: (p: { id: number; note: string }) => http.post<StockRequestDto>(`/crm/requests/${p.id}/cancel`, { note: p.note }),
      onSuccess: done,
    }),
    seen: useMutation({ mutationFn: () => http.post<{ marked: number }>('/crm/requests/alerts/seen'), onSuccess: done }),
  };
}

/* ---------- Reposição e pedidos de compra (spec crm/11) ---------- */

export function usePlanning(horizon: number) {
  return useQuery({
    queryKey: [...qk.planning, horizon],
    queryFn: () => http.get<PlanningDto>('/crm/stock/planning', { horizon }),
    placeholderData: keepPreviousData,
  });
}

export function usePurchaseOrders(status: PurchaseOrderStatus | undefined, late: boolean, page: number) {
  return useQuery({
    queryKey: [...qk.purchaseOrders, 'list', status ?? '', late, page],
    queryFn: () => http.get<Page<PurchaseOrderDto>>('/crm/purchase-orders', { status, late: late || undefined, page, size: 20 }),
    placeholderData: keepPreviousData,
  });
}

// Receber mexe no estoque (e fecha chamados): derruba o cache de tudo que depende do estoque.
export function usePurchaseOrderActions() {
  const invalidate = useInvalidate();
  const done = () => invalidate(qk.purchaseOrders, qk.planning);
  return {
    create: useMutation({ mutationFn: (input: PurchaseOrderInput) => http.post<PurchaseOrderDto>('/crm/purchase-orders', input), onSuccess: done }),
    // Etapas (V11): saiu do fornecedor (quem compra) → chegou na loja (quem recebe) → entrada com a nota fiscal.
    ship: useMutation({
      mutationFn: (p: { id: number; invoiceNumber?: string }) => http.post<PurchaseOrderDto>(`/crm/purchase-orders/${p.id}/ship`, { invoiceNumber: p.invoiceNumber }),
      onSuccess: done,
    }),
    arrive: useMutation({
      mutationFn: (id: number) => http.post<PurchaseOrderDto>(`/crm/purchase-orders/${id}/arrive`),
      onSuccess: done,
    }),
    // Conferência: items = o que chegou de cada peça; bonusInvoiceNumber só se veio a mais.
    receive: useMutation({
      mutationFn: (p: { id: number; invoiceNumber: string; bonusInvoiceNumber?: string; note?: string; items: { productId: number; receivedQuantity: number }[] }) =>
        http.post<PurchaseReceivedDto>(`/crm/purchase-orders/${p.id}/receive`, { invoiceNumber: p.invoiceNumber, bonusInvoiceNumber: p.bonusInvoiceNumber, note: p.note, items: p.items }),
      onSuccess: () => invalidate(...STOCK_SENSITIVE),
    }),
    // A nota volta ao fornecedor: nada entra, o pedido volta a "pedido feito".
    returnDelivery: useMutation({
      mutationFn: (p: { id: number; note: string; items?: { productId: number; receivedQuantity: number }[] }) =>
        http.post<PurchaseOrderDto>(`/crm/purchase-orders/${p.id}/return`, { note: p.note, items: p.items }),
      onSuccess: done,
    }),
    reschedule: useMutation({
      mutationFn: (p: { id: number; expectedDate: string; note: string }) =>
        http.post<PurchaseOrderDto>(`/crm/purchase-orders/${p.id}/reschedule`, { expectedDate: p.expectedDate, note: p.note }),
      onSuccess: done,
    }),
    cancel: useMutation({
      mutationFn: (p: { id: number; note: string }) => http.post<PurchaseOrderDto>(`/crm/purchase-orders/${p.id}/cancel`, { note: p.note }),
      onSuccess: done,
    }),
  };
}

/* ---------- Agente de vendas da demonstração (spec crm/08) ---------- */

export function useDemoAgent() {
  return useQuery({ queryKey: qk.demoAgent, queryFn: () => http.get<DemoAgentDiaryDto>('/crm/demo-agent') });
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
    // Senha provisória nova com troca obrigatória; module = 'loja' para a conta de um cliente.
    resetPassword: useMutation({
      mutationFn: (p: { id: number; module?: 'crm' | 'loja' }) =>
        http.post<{ user: UserRowDto; provisionalPassword: string }>(`/admin/users/${p.id}/reset-password?module=${p.module ?? 'crm'}`),
      onSuccess: () => invalidate(qk.users, qk.customers),
    }),
  };
}

// Spec crm/12: previsão de vendas e metas sugeridas (só gerente).
export function useForecast(months: number) {
  return useQuery({
    queryKey: [...qk.forecast, months],
    queryFn: () => http.get<SalesForecastDto>('/crm/forecast', { months }),
    placeholderData: keepPreviousData,
  });
}

export function useTargetSuggestions(month: string, growth: number, enabled: boolean) {
  return useQuery({
    queryKey: [...qk.targetSuggestions, month, growth],
    queryFn: () => http.get<TargetSuggestionsDto>('/crm/targets/suggestions', { month, growth }),
    placeholderData: keepPreviousData,
    enabled,
  });
}
