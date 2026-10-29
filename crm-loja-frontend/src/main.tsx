import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { Providers } from './app/providers';
import { router } from './app/router';
import './presentation/styles/global.css';

// Ponto de entrada do front. StrictMode roda verificações extras só em desenvolvimento; as rotas ficam em app/router.tsx.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Providers>
      <RouterProvider router={router} />
    </Providers>
  </StrictMode>,
);
