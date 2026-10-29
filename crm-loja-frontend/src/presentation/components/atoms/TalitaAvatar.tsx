// Rosto da Talita, desenhado em SVG com a paleta da Orden (Areia, Caramelo, Café e Creme).
// Cores fixas de propósito: o avatar tem a mesma cara nos temas claro e escuro.
export function TalitaAvatar({ className = 'size-10' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} role="img" aria-label="Talita">
      <circle cx="32" cy="32" r="32" fill="#d8c7b0" />
      {/* cabelo atrás, com coque */}
      <circle cx="32" cy="11" r="7" fill="#29231f" />
      <path d="M17 33c0-12 6-19 15-19s15 7 15 19c0 4-1 8-3 10H20c-2-2-3-6-3-10z" fill="#29231f" />
      {/* blusa e pescoço */}
      <path d="M8 64c1-12 9-19 24-19s23 7 24 19z" fill="#6b4f3a" />
      <path d="M27 42h10v7c0 3-2 5-5 5s-5-2-5-5z" fill="#e8d3bd" />
      {/* rosto */}
      <ellipse cx="32" cy="31" rx="10.5" ry="12" fill="#f1dcc6" />
      {/* franja */}
      <path d="M21.5 29c1-8 6-11 11-11 5 0 9 3 10 11-3-4-6-5-10-5-5 0-8 2-11 5z" fill="#29231f" />
      {/* olhos e sorriso */}
      <circle cx="27.5" cy="32" r="1.3" fill="#29231f" />
      <circle cx="36.5" cy="32" r="1.3" fill="#29231f" />
      <path d="M28 37.5c1.2 1.6 2.4 2.2 4 2.2s2.8-.6 4-2.2" fill="none" stroke="#6b4f3a" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}
