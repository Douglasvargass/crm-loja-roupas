import type { Role } from '@/data/dto';

// Espelho do contrato da API (seção 6). O front só esconde e redireciona; quem barra é o back-end.
export const can = {
  dashboard: (r: Role) => r === 'MANAGER',
  customers: (r: Role) => r === 'MANAGER' || r === 'SELLER',
  deleteCustomer: (r: Role) => r === 'MANAGER',
  categories: () => true,
  manageCategories: (r: Role) => r === 'MANAGER',
  products: () => true,
  manageProducts: (r: Role) => r === 'MANAGER' || r === 'STOCK_KEEPER',
  deleteProduct: (r: Role) => r === 'MANAGER',
  stock: (r: Role) => r === 'MANAGER' || r === 'STOCK_KEEPER',
  sales: (r: Role) => r === 'MANAGER' || r === 'SELLER',
  cancelSale: (r: Role) => r === 'MANAGER',
  users: (r: Role) => r === 'MANAGER',
  // Todos os papéis; o back-end entrega ao assistente só as consultas que o papel pode ver.
  assistant: () => true,
};

// Tela inicial por papel (HU33).
export const homeOf = (r: Role) => (r === 'MANAGER' ? '/' : r === 'SELLER' ? '/vendas' : '/estoque');
