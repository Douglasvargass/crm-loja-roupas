import { createBrowserRouter, Navigate } from 'react-router';
import { NotFound } from '@/presentation/pages/NotFound';
import { can } from '@/domain/permissions';
import { AppShell } from '@/presentation/components/templates/AppShell';
import { CategoriesPage } from '@/presentation/pages/CategoriesPage';
import { ChangePasswordPage } from '@/presentation/pages/ChangePasswordPage';
import { CustomerDetailPage } from '@/presentation/pages/customers/CustomerDetailPage';
import { CustomersPage } from '@/presentation/pages/customers/CustomersPage';
import { DashboardPage } from '@/presentation/pages/DashboardPage';
import { LoginPage } from '@/presentation/pages/LoginPage';
import { MyPanelPage } from '@/presentation/pages/MyPanelPage';
import { ProductDetailPage } from '@/presentation/pages/products/ProductDetailPage';
import { ProductsPage } from '@/presentation/pages/products/ProductsPage';
import { NewSalePage } from '@/presentation/pages/sales/NewSalePage';
import { SaleDetailPage } from '@/presentation/pages/sales/SaleDetailPage';
import { SalesPage } from '@/presentation/pages/sales/SalesPage';
import { RequestsPage } from '@/presentation/pages/RequestsPage';
import { PurchaseOrdersPage } from '@/presentation/pages/PurchaseOrdersPage';
import { ReplenishmentPage } from '@/presentation/pages/ReplenishmentPage';
import { StockPage } from '@/presentation/pages/StockPage';
import { TargetsPage } from '@/presentation/pages/TargetsPage';
import { ForecastPage } from '@/presentation/pages/ForecastPage';
import { CatalogPage } from '@/presentation/pages/CatalogPage';
import { CouponsPage } from '@/presentation/pages/CouponsPage';
import { UsersPage } from '@/presentation/pages/UsersPage';
import { RequireActivePassword, RequireAuth, RequireCustomer, RequireRole, RequireStaff } from './guards';
import { StoreShell } from '@/presentation/components/templates/StoreShell';
import { CartPage, MyDataPage, OrderDetailPage, OrdersPage, StorePage } from '@/presentation/pages/store/StorePages';
import { StorePreviewLayout } from '@/presentation/pages/store/StorePreview';

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
            element: <RequireCustomer />,
            children: [
              {
                element: <StoreShell />,
                children: [
                  { path: 'loja', element: <StorePage /> },
                  { path: 'loja/carrinho', element: <CartPage /> },
                  { path: 'loja/pedidos', element: <OrdersPage /> },
                  { path: 'loja/pedidos/:id', element: <OrderDetailPage /> },
                  { path: 'loja/meus-dados', element: <MyDataPage /> },
                ],
              },
            ],
          },
          {
            element: <RequireStaff />,
            children: [
            // Prévia da loja (gerente e equipe técnica): o layout do cliente, fora do AppShell, só para olhar.
            {
              path: 'previa-loja',
              element: <RequireRole allow={can.storePreview}><StorePreviewLayout /></RequireRole>,
              children: [
                { index: true, element: <StorePage /> },
                { path: 'carrinho', element: <CartPage /> },
                { path: 'pedidos', element: <OrdersPage /> },
                { path: 'meus-dados', element: <MyDataPage /> },
              ],
            },
            {
            element: <AppShell />,
            children: [
              { index: true, element: <RequireRole allow={can.dashboardPage}><DashboardPage /></RequireRole> },
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
              { path: 'meu-painel', element: <RequireRole allow={can.myPanel}><MyPanelPage /></RequireRole> },
              { path: 'metas', element: <RequireRole allow={can.targets}><TargetsPage /></RequireRole> },
              { path: 'catalogo', element: <CatalogPage /> },
              { path: 'cupons', element: <RequireRole allow={can.coupons}><CouponsPage /></RequireRole> },
              { path: 'previsao', element: <RequireRole allow={can.forecast}><ForecastPage /></RequireRole> },
              { path: 'chamados', element: <RequireRole allow={can.requests}><RequestsPage /></RequireRole> },
              { path: 'reposicao', element: <RequireRole allow={can.replenishment}><ReplenishmentPage /></RequireRole> },
              { path: 'pedidos-compra', element: <RequireRole allow={can.replenishment}><PurchaseOrdersPage /></RequireRole> },
              // A antiga tela do assistente virou a Thalita (janela flutuante); quem tinha o link salvo vai ao início.
              { path: 'assistente', element: <Navigate to="/" replace /> },
              { path: '*', element: <NotFound /> },
            ],
          }],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
