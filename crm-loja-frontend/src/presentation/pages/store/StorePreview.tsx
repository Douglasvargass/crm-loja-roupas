import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { formatMoney } from '@/lib/format';
import { Button } from '../../components/atoms/Button';
import { Input } from '../../components/atoms/Field';
import { FakePixQr } from '../../components/molecules/FakePixQr';
import { Card, PageHeader } from '../../components/templates/Page';
import { StoreShell } from '../../components/templates/StoreShell';
import { PreviewContext, useStorePreview, type Preview, type PreviewItem } from './previewContext';

// "Prévia da loja" (pedido do Douglas, 29/10/2026): o gerente e a equipe técnica veem o espaço do cliente como QUALQUER
// cliente vê, sem escolher uma pessoa. A vitrine é a de verdade (mesma API, mesmos produtos e preços); o carrinho vive só
// na memória desta tela e nada é gravado: não há pagamento, pedido nem dado de cliente. Por isso não usa o "Ver como",
// que precisa de uma pessoa real para imitar.

// Rota /previa-loja: o mesmo layout da loja, com o carrinho de mentira.
export function StorePreviewLayout() {
  const [items, setItems] = useState<PreviewItem[]>([]);
  const value = useMemo<Preview>(() => ({
    base: '/previa-loja',
    items,
    count: items.reduce((n, i) => n + i.quantity, 0),
    total: items.reduce((n, i) => n + i.quantity * i.product.price, 0),
    add: (p, qty) => setItems((list) => {
      const found = list.find((i) => i.product.id === p.id);
      return found
        ? list.map((i) => (i.product.id === p.id ? { ...i, quantity: Math.min(99, i.quantity + qty) } : i))
        : [...list, { product: p, quantity: qty }];
    }),
    change: (id, qty) => setItems((list) => list.map((i) => (i.product.id === id ? { ...i, quantity: Math.max(1, Math.min(99, qty)) } : i))),
    remove: (id) => setItems((list) => list.filter((i) => i.product.id !== id)),
  }), [items]);
  return (
    <PreviewContext.Provider value={value}>
      <StoreShell />
    </PreviewContext.Provider>
  );
}

export function PreviewCartPage() {
  const pv = useStorePreview()!;
  const [method, setMethod] = useState<'PIX' | 'CARD'>('PIX');
  return (
    <>
      <PageHeader title="Carrinho" subtitle="Confira as peças e pague."
        help="Na prévia o carrinho fica só nesta tela: nada é reservado, pago ou gravado." />
      {pv.items.length === 0 ? (
        <Card><p className="p-6 text-center text-sm text-muted">Seu carrinho está vazio. Que tal dar uma olhada na vitrine?</p></Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          <Card title="Peças" className="lg:col-span-2">
            <ul className="divide-y divide-border">
              {pv.items.map(({ product: p, quantity }) => (
                <li key={p.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                  <div className="min-w-0 flex-1">
                    <div className="font-medium">{p.name}</div>
                    <div className="text-xs text-muted">Tamanho {p.size} · {p.color} · {formatMoney(p.price)} cada</div>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button size="sm" variant="secondary" aria-label="Diminuir" disabled={quantity <= 1}
                      title={quantity <= 1 ? 'Para tirar a peça, use Remover' : undefined} onClick={() => pv.change(p.id, quantity - 1)}>−</Button>
                    <span className="tabular w-8 text-center">{quantity}</span>
                    <Button size="sm" variant="secondary" aria-label="Aumentar" onClick={() => pv.change(p.id, quantity + 1)}>+</Button>
                  </div>
                  <span className="tabular w-24 text-right">{formatMoney(p.price * quantity)}</span>
                  <Button size="sm" variant="ghost" onClick={() => pv.remove(p.id)}>Remover</Button>
                </li>
              ))}
            </ul>
          </Card>
          <Card title="Pagamento">
            <div className="flex flex-col gap-3 p-4">
              <p className="flex items-baseline justify-between text-sm">
                Total <strong className="tabular text-xl">{formatMoney(pv.total)}</strong>
              </p>
              <fieldset className="flex flex-col gap-2 text-sm">
                <legend className="mb-1 font-medium">Como quer pagar?</legend>
                <label className="flex items-center gap-2"><input type="radio" checked={method === 'PIX'} onChange={() => setMethod('PIX')} /> Pix</label>
                <label className="flex items-center gap-2"><input type="radio" checked={method === 'CARD'} onChange={() => setMethod('CARD')} /> Cartão de crédito</label>
              </fieldset>
              {method === 'PIX' && <FakePixQr amount={pv.total} />}
              {method === 'CARD' && <Input label="Número do cartão" disabled placeholder="0000 0000 0000 0000" hint="Na prévia o cartão não é digitado." />}
              {/* Botão travado sempre com o motivo ao lado (padrão do projeto). */}
              <Button disabled title="Prévia: o pagamento fica desligado para a equipe.">Pagar {formatMoney(pv.total)}</Button>
              <p className="text-xs text-muted">Prévia: o pagamento fica desligado. Para o cliente, este botão confirma o pedido na hora.</p>
            </div>
          </Card>
        </div>
      )}
      <p className="mt-4 text-sm"><Link to={pv.base} className="text-primary hover:underline">← Continuar comprando</Link></p>
    </>
  );
}

export function PreviewOrdersPage() {
  return (
    <>
      <PageHeader title="Meus pedidos" subtitle="Tudo o que você comprou, online e na loja."
        help="Na prévia não há pedidos: cada cliente vê aqui só os pedidos dele." />
      <Card>
        <p className="p-6 text-center text-sm text-muted">
          Na prévia não aparece nenhum pedido. O cliente vê aqui a lista das compras dele (online e na loja), com a situação de cada uma.
        </p>
      </Card>
    </>
  );
}

// Campos de exemplo, travados: mostram o formulário que o cliente usa sem expor os dados de ninguém.
export function PreviewMyDataPage() {
  const hint = 'Prévia: campo de exemplo.';
  return (
    <>
      <PageHeader title="Meus dados" subtitle="Contato e endereço."
        help="Na prévia os campos são de exemplo e não salvam. O cliente muda telefone, e-mail e endereço; nome e CPF só a loja muda." />
      <Card>
        <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2">
          <Input label="Nome" value="Cliente de exemplo" disabled hint="Só a loja muda." />
          <Input label="CPF" value="000.000.000-00" disabled hint="Só a loja muda." />
          <Input label="Telefone" value="(48) 99999-0000" disabled hint={hint} />
          <Input label="E-mail" value="cliente@exemplo.com" disabled hint={hint} />
          <Input label="CEP" value="88701-000" disabled hint="Para o cliente, o endereço é preenchido pelo CEP." />
          <Input label="Rua" value="Avenida Marcolino Martins Cabral" disabled hint={hint} />
          <div className="flex items-end sm:col-span-2">
            <Button disabled title="Prévia: nada é salvo.">Salvar</Button>
            <span className="ml-3 text-xs text-muted">Prévia: nada é salvo.</span>
          </div>
        </div>
      </Card>
    </>
  );
}
