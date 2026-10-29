import type { Role } from '@/data/dto';

// Espelho do contrato da API (seção 6). O front só esconde e redireciona; quem barra é o back-end.
// Equipe técnica (DEVELOPER, MARKETING_DEV) vale por gerente em tudo, igual ao RbacService do back-end.
const asManager = (r: Role) => r === 'MANAGER' || r === 'DEVELOPER' || r === 'MARKETING_DEV';
// Operações e Compras vale por estoquista (RbacService.IMPLIES no back-end).
const asStock = (r: Role) => r === 'STOCK_KEEPER' || r === 'PURCHASING';

export const can = {
  // Vendas e faturamento do dashboard: só gerente e equipe técnica.
  dashboard: asManager,
  // Abrir a tela Dashboard: quem cuida do estoque também, vendo só o bloco de estoque (pedido do Douglas, 29/10).
  dashboardPage: (r: Role) => asManager(r) || asStock(r),
  customers: (r: Role) => asManager(r) || r === 'SELLER',
  deleteCustomer: asManager,
  categories: () => true,
  manageCategories: asManager,
  products: () => true,
  manageProducts: (r: Role) => asManager(r) || asStock(r),
  deleteProduct: asManager,
  stock: (r: Role) => asManager(r) || asStock(r),
  sales: (r: Role) => asManager(r) || r === 'SELLER',
  cancelSale: asManager,
  users: asManager,
  // Meu painel é de quem vende; a equipe técnica e o gerente acompanham pela tela Metas.
  myPanel: (r: Role) => r === 'SELLER',
  targets: asManager,
  // HU32: baixar listas em planilha.
  export: asManager,
  // Chamados (spec crm/09): todos veem; quem atende abre; quem cuida do estoque resolve.
  requests: () => true,
  openRequest: (r: Role) => asManager(r) || r === 'SELLER',
  resolveRequest: (r: Role) => asManager(r) || asStock(r),
  // Reposição e pedidos de compra (spec crm/11): quem cuida do estoque vê e recebe; quem compra registra.
  replenishment: (r: Role) => asManager(r) || asStock(r),
  buy: (r: Role) => asManager(r) || r === 'PURCHASING',
  receivePurchase: (r: Role) => asManager(r) || asStock(r),
  // Prévia da loja: ver o site como o cliente vê (mesmos papéis do "Ver como").
  storePreview: asManager,
};

// Tela inicial por papel (HU33).
export const homeOf = (r: Role) =>
  r === 'CUSTOMER' ? '/loja'
    : asManager(r) ? '/' : r === 'SELLER' ? '/meu-painel' : r === 'PURCHASING' ? '/' : '/estoque';

// Espaço do cliente (spec crm/07): cliente só vê a loja; a equipe só vê o sistema interno.
export const isCustomer = (r: Role | null) => r === 'CUSTOMER';
