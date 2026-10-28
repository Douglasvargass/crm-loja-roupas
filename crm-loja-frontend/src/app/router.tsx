import { createBrowserRouter, Navigate } from 'react-router';
import { NotFound } from '@/presentation/pages/NotFound';
import { can } from '@/domain/permissions';
import { AppShell } from '@/presentation/components/templates/AppShell';
import { AssistantPage } from '@/presentation/pages/AssistantPage';
import { CategoriesPage } from '@/presentation/pages/CategoriesPage';
import { ChangePasswordPage } from '@/presentation/pages/ChangePasswordPage';
import { CustomerDetailPage } from '@/presentation/pages/customers/CustomerDetailPage';
import { CustomersPage } from '@/presentation/pages/customers/CustomersPage';
import { DashboardPage } from '@/presentation/pages/DashboardPage';
import { LoginPage } from '@/presentation/pages/LoginPage';
import { ProductDetailPage } from '@/presentation/pages/products/ProductDetailPage';
import { ProductsPage } from '@/presentation/pages/products/ProductsPage';
import { NewSalePage } from '@/presentation/pages/sales/NewSalePage';
import { SaleDetailPage } from '@/presentation/pages/sales/SaleDetailPage';
import { SalesPage } from '@/presentation/pages/sales/SalesPage';
import { StockPage } from '@/presentation/pages/StockPage';
import { UsersPage } from '@/presentation/pages/UsersPage';
import { RequireActivePassword, RequireAuth, RequireRole } from './guards';

// Telas internas: RequireAuth → RequireActivePassword → AppShell (seção 7 da documentação).
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/trocar-senha', element: <ChangePasswordPage /> },
      {
        element: <RequireActivePassword />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <RequireRole allow={can.dashboard}><DashboardPage /></RequireRole> },
              { path: 'clientes', element: <RequireRole allow={can.customers}><CustomersPage /></RequireRole> },
              { path: 'clientes/:id', element: <RequireRole allow={can.customers}><CustomerDetailPage /></RequireRole> },
              { path: 'categorias', element: <RequireRole allow={can.categories}><CategoriesPage /></RequireRole> },
              { path: 'produtos', element: <RequireRole allow={can.products}><ProductsPage /></RequireRole> },
              { path: 'produtos/:id', element: <RequireRole allow={can.products}><ProductDetailPage /></RequireRole> },
              { path: 'estoque', element: <RequireRole allow={can.stock}><StockPage /></RequireRole> },
              { path: 'vendas', element: <RequireRole allow={can.sales}><SalesPage /></RequireRole> },
              { path: 'vendas/nova', element: <RequireRole allow={can.sales}><NewSalePage /></RequireRole> },
              { path: 'vendas/:id', element: <RequireRole allow={can.sales}><SaleDetailPage /></RequireRole> },
              { path: 'usuarios', element: <RequireRole allow={can.users}><UsersPage /></RequireRole> },
              { path: 'assistente', element: <RequireRole allow={can.assistant}><AssistantPage /></RequireRole> },
              { path: '*', element: <NotFound /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
