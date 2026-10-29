import { formatMoney } from '@/lib/format';

// QR code de Pix DE MENTIRA (espaço do cliente, spec crm/07): o pagamento é simulado, então a imagem só imita o
// visual de um QR code para a demonstração parecer real. Não é um código válido: nenhum app de banco consegue ler,
// e a faixa "DEMONSTRAÇÃO" no meio deixa isso claro. Desenhado à mão em SVG, sem biblioteca nova.
const N = 25;

// Gerador de números simples e fixo pela semente: o mesmo total desenha sempre o mesmo "QR".
function rng(seed: number) {
  let s = seed % 2147483647 || 1;
  return () => (s = (s * 48271) % 2147483647) / 2147483647;
}

// Os 3 quadrados dos cantos que todo QR code tem.
const isFinder = (x: number, y: number) => {
  const inBox = (bx: number, by: number) => x >= bx && x < bx + 7 && y >= by && y < by + 7;
  return inBox(0, 0) || inBox(N - 7, 0) || inBox(0, N - 7);
};

const finderOn = (x: number, y: number) => {
  const lx = x >= N - 7 ? x - (N - 7) : x;
  const ly = y >= N - 7 ? y - (N - 7) : y;
  const edge = lx === 0 || lx === 6 || ly === 0 || ly === 6;
  const core = lx >= 2 && lx <= 4 && ly >= 2 && ly <= 4;
  return edge || core;
};

export function FakePixQr({ amount }: { amount: number }) {
  const next = rng(Math.round(amount * 100) + 7);
  const cells: string[] = [];
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const on = isFinder(x, y) ? finderOn(x, y) : next() > 0.52;
      if (on) cells.push(`M${x} ${y}h1v1h-1z`);
    }
  }
  const fakeCode = `00020126-ORDEN-DEMO-${String(Math.round(amount * 100)).padStart(8, '0')}-NAO-PAGAR`;

  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-border bg-surface-2 p-3 text-center">
      <div className="relative">
        <svg viewBox={`-2 -2 ${N + 4} ${N + 4}`} className="size-44 rounded-md bg-white" role="img"
          aria-label="QR code de demonstração, não é um Pix de verdade" shapeRendering="crispEdges">
          <path d={cells.join('')} fill="#29231f" />
        </svg>
        <span className="absolute inset-x-0 top-1/2 -translate-y-1/2 bg-primary/90 py-0.5 text-[10px] font-bold tracking-widest text-on-primary">
          DEMONSTRAÇÃO
        </span>
      </div>
      <p className="text-sm font-medium">Pix de {formatMoney(amount)}</p>
      <p className="w-full truncate rounded bg-surface px-2 py-1 font-mono text-[11px] text-muted" title={fakeCode}>{fakeCode}</p>
      <p className="text-xs text-muted">
        QR code de mentira, só para a demonstração: nenhum banco consegue ler e nada é cobrado.
        Clique em <strong>Pagar</strong> para simular o pagamento aprovado.
      </p>
    </div>
  );
}
