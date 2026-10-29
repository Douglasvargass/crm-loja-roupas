import { Link } from 'react-router';

// Endereço que não existe dentro do sistema: oferece voltar ao início em vez de uma tela em branco.
export function NotFound() {
  return (
    <div className="p-8 text-center">
      <h1 className="text-lg font-semibold">Página não encontrada</h1>
      <Link to="/" className="mt-2 inline-block text-primary hover:underline">Voltar ao início</Link>
    </div>
  );
}
