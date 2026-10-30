import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useToast } from '@/app/context/ToastContext';
import { paymentLabel, saleStatusLabel, saleStatusTone } from '@/data/adapters';
import type { MyDataInput, ShopProductDto } from '@/data/dto';
import { ApiError } from '@/data/http';
import { useProductImages, useCart, useMyData, useMyOrder, useMyOrders, useMyPartRequests, useRequestPart, useShopCategories, useShopProducts, useStoreActions } from '@/data/hooks';
import { formatDateTime, formatMoney, maskPhone, maskZip } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import { lookupZip } from '@/lib/viacep';
import { Badge } from '../../components/atoms/Badge';
import { ProductPhoto } from '../../components/atoms/ProductPhoto';
import { Lightbox } from '../../components/organisms/ProductGallery';
import { Button } from '../../components/atoms/Button';
import { Input, Select } from '../../components/atoms/Field';
import { Modal } from '../../components/molecules/Modal';
import { Icon } from '../../components/atoms/Icon';
import { FakePixQr } from '../../components/molecules/FakePixQr';
import { Pagination } from '../../components/molecules/Pagination';
import { QueryState } from '../../components/molecules/QueryState';
import { Card, PageHeader } from '../../components/templates/Page';
import { useStorePreview } from './previewContext';
import { PreviewCartPage, PreviewMyDataPage, PreviewOrdersPage } from './StorePreview';

// Espaço do cliente (spec crm/07). Cinco telas: vitrine, carrinho com pagamento, meus pedidos, pedido e meus dados.

/* ------------------------------------------------------------------------------------------------ vitrine */

export function StorePage() {
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [size, setSize] = useState('');
  const [page, setPage] = useState(0);
  const categories = useShopCategories();
  const query = useShopProducts({ name: useDebounced(name) || undefined, categoryId: categoryId ? Number(categoryId) : undefined,
    size: useDebounced(size) || undefined, page });
  const pv = useStorePreview();
  const [asking, setAsking] = useState(false);
  return (
    <>
      <PageHeader title="Vitrine" subtitle="Peças disponíveis agora na Orden."
        help="Só aparece o que tem em estoque. O preço é o de agora; se mudar enquanto a peça está no carrinho, avisamos antes de pagar. Não achou a peça? Peça à loja: avisamos você em Meus pedidos."
        actions={
          // Pedido de peça que a loja não tem (29/10/2026): vira um chamado que o gerente e o estoque veem no sino.
          <span className="flex flex-col items-end gap-1">
            <Button variant="secondary" disabled={!!pv} onClick={() => setAsking(true)}
              title={pv ? 'Prévia: pedir peça fica desligado para a equipe.' : undefined}>
              Não achou? Peça à loja
            </Button>
            {pv && <span className="text-xs text-muted">Prévia: para o cliente, abre o pedido de uma peça.</span>}
          </span>
        } />
      {asking && <RequestPartModal onClose={() => setAsking(false)} />}
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Input label="Buscar" placeholder="Ex.: camiseta" value={name} onChange={(e) => { setName(e.target.value); setPage(0); }} />
        <Select label="Categoria" value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
          <option value="">Todas</option>
          {(categories.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
        <Input label="Tamanho" placeholder="Ex.: M ou 40" value={size} onChange={(e) => { setSize(e.target.value); setPage(0); }} />
      </div>
      <QueryState query={query} isEmpty={(d) => d.content.length === 0} empty="Nenhuma peça encontrada com esses filtros.">
        {(d) => (
          <>
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {d.content.map((p) => <ProductCard key={p.id} p={p} />)}
            </ul>
            <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} onChange={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}

function RequestPartModal({ onClose }: { onClose: () => void }) {
  const ask = useRequestPart();
  const toast = useToast();
  const [f, setF] = useState({ description: '', size: '', color: '', quantity: '1' });
  const [error, setError] = useState<string>();

  async function submit(e: FormEvent) {
    e.preventDefault();
    const quantity = Number(f.quantity);
    if (f.description.trim().length < 3) return setError('Descreva a peça que você procura.');
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) return setError('A quantidade deve ser de 1 a 99.');
    setError(undefined);
    try {
      await ask.mutateAsync({ description: f.description.trim(), size: f.size.trim() || undefined, color: f.color.trim() || undefined, quantity });
      toast.success('Pedido de peça enviado! A loja foi avisada; acompanhe em Meus pedidos.');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal open title="Pedir uma peça à loja" onClose={onClose}
      footer={<><Button variant="secondary" onClick={onClose}>Voltar</Button><Button type="submit" form="part-form" loading={ask.isPending}>Enviar pedido</Button></>}>
      <form id="part-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p className="text-sm text-muted">Procura uma peça que não está na vitrine? Conte qual é: a loja recebe o aviso e você acompanha em Meus pedidos. Não é uma compra: nada é cobrado.</p>
        <Input label="Qual peça você procura" value={f.description} placeholder="ex.: Vestido longo verde" onChange={(e) => setF({ ...f, description: e.target.value })} />
        <div className="grid grid-cols-3 gap-2">
          <Input label="Tamanho" value={f.size} onChange={(e) => setF({ ...f, size: e.target.value })} />
          <Input label="Cor" value={f.color} onChange={(e) => setF({ ...f, color: e.target.value })} />
          <Input label="Quantidade" type="number" min={1} max={99} value={f.quantity} onChange={(e) => setF({ ...f, quantity: e.target.value })} />
        </div>
        {error && <p className="text-sm text-error">{error}</p>}
      </form>
    </Modal>
  );
}

function ProductCard({ p }: { p: ShopProductDto }) {
  const { add } = useStoreActions();
  const toast = useToast();
  const pv = useStorePreview();
  const [qty, setQty] = useState(1);
  async function addToCart() {
    // Prévia da loja: o carrinho é só desta tela, nada vai ao servidor.
    if (pv) {
      pv.add(p, qty);
      toast.success(`${p.name} no carrinho (prévia).`);
      return;
    }
    try {
      await add.mutateAsync({ productId: p.id, quantity: qty });
      toast.success(`${p.name} no carrinho.`);
    } catch (err) {
      toast.error(err);
    }
  }
  return (
    <li className="flex flex-col gap-2 overflow-hidden rounded-xl border border-border bg-surface p-4">
      <StorePhotos id={p.id} name={p.name} />
      <div className="flex items-start justify-between gap-2">
        <span className="text-xs text-muted">{p.category}</span>
        {p.lastUnits && <Badge tone="warning">Últimas unidades</Badge>}
      </div>
      <h2 className="font-semibold">{p.name}</h2>
      <p className="text-sm text-muted">Tamanho {p.size} · {p.color}</p>
      {p.description && <p className="text-sm">{p.description}</p>}
      <div className="mt-auto flex items-end justify-between gap-2 pt-2">
        <span className="tabular text-lg font-semibold">{formatMoney(p.price)}</span>
        <span className="flex items-center gap-2">
          <input aria-label={`Quantidade de ${p.name}`} type="number" min={1} max={99} value={qty}
            onChange={(e) => setQty(Math.max(1, Math.min(99, Number(e.target.value) || 1)))}
            className="h-9 w-16 rounded-md border border-border bg-surface px-2 text-sm tabular" />
          <Button size="sm" loading={add.isPending} onClick={() => void addToCart()}>
            <Icon name="plus" className="size-4" />Carrinho
          </Button>
        </span>
      </div>
    </li>
  );
}

// Foto da peça na vitrine; tocar abre todas as fotos em tela cheia.
function StorePhotos({ id, name }: { id: number; name: string }) {
  const [open, setOpen] = useState(false);
  const images = useProductImages(open ? id : 0);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} aria-label={`Ver as fotos de ${name}`} className="group -mx-4 -mt-4 mb-1 overflow-hidden">
        <ProductPhoto productId={id} alt={name} className="aspect-square w-full transition-transform duration-300 group-hover:scale-105" />
      </button>
      {open && images.data && images.data.length > 0 && <Lightbox images={images.data} start={0} name={name} onClose={() => setOpen(false)} />}
    </>
  );
}

/* ------------------------------------------------------------------------------------------------ carrinho */

// Na Prévia da loja cada tela troca pela versão sem servidor (StorePreview.tsx); a do cliente fica igual.
export function CartPage() {
  return useStorePreview() ? <PreviewCartPage /> : <CustomerCartPage />;
}

function CustomerCartPage() {
  const cart = useCart();
  const { change, remove, checkout } = useStoreActions();
  const toast = useToast();
  const navigate = useNavigate();
  const [method, setMethod] = useState<'PIX' | 'CARD'>('PIX');
  const [card, setCard] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  async function pay(e: FormEvent) {
    e.preventDefault();
    setNotice(null);
    try {
      const order = await checkout.mutateAsync(method === 'PIX' ? { method } : { method, cardNumber: card });
      toast.success(`Pedido nº ${order.sale.id} confirmado!`);
      navigate(`/loja/pedidos/${order.sale.id}`);
    } catch (err) {
      // Preço mudou: o carrinho já foi atualizado no servidor; mostra o aviso perto do total, não só no toast.
      if (err instanceof ApiError && (err.code === 'price_changed' || err.code === 'payment_declined')) setNotice(err.message);
      else toast.error(err);
    }
  }

  return (
    <>
      <PageHeader title="Carrinho" subtitle="Confira as peças e pague."
        help="O carrinho não reserva as peças: o estoque é conferido na hora de pagar, e se faltar algo nada é cobrado." />
      <QueryState query={cart} isEmpty={(c) => c.items.length === 0}
        empty="Seu carrinho está vazio. Que tal dar uma olhada na vitrine?">
        {(c) => (
          <div className="grid gap-4 lg:grid-cols-3">
            <Card title="Peças" className="lg:col-span-2">
              <ul className="divide-y divide-border">
                {c.items.map((i) => (
                  <li key={i.itemId} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                    <div className="min-w-0 flex-1">
                      <div className="font-medium">{i.name}</div>
                      <div className="text-xs text-muted">{i.code} · {formatMoney(i.unitPrice)} cada</div>
                      {!i.available && <div className="text-xs text-error">Não temos essa quantidade agora: diminua para conseguir pagar.</div>}
                    </div>
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant="secondary" aria-label="Diminuir" title={i.quantity <= 1 ? 'Para tirar a peça, use Remover' : undefined}
                        disabled={i.quantity <= 1 || change.isPending} onClick={() => change.mutate({ itemId: i.itemId, quantity: i.quantity - 1 })}>−</Button>
                      <span className="tabular w-8 text-center">{i.quantity}</span>
                      <Button size="sm" variant="secondary" aria-label="Aumentar" disabled={change.isPending}
                        onClick={() => change.mutate({ itemId: i.itemId, quantity: i.quantity + 1 }, { onError: (err) => toast.error(err) })}>+</Button>
                    </div>
                    <span className="tabular w-24 text-right">{formatMoney(i.subtotal)}</span>
                    <Button size="sm" variant="ghost" onClick={() => remove.mutate(i.itemId)}>Remover</Button>
                  </li>
                ))}
              </ul>
            </Card>
            <Card title="Pagamento">
              <form onSubmit={pay} className="flex flex-col gap-3 p-4">
                <p className="flex items-baseline justify-between text-sm">
                  Total <strong className="tabular text-xl">{formatMoney(c.total)}</strong>
                </p>
                {notice && <p role="alert" className="rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-sm text-warning">{notice}</p>}
                <fieldset className="flex flex-col gap-2 text-sm">
                  <legend className="mb-1 font-medium">Como quer pagar?</legend>
                  <label className="flex items-center gap-2"><input type="radio" checked={method === 'PIX'} onChange={() => setMethod('PIX')} /> Pix</label>
                  <label className="flex items-center gap-2"><input type="radio" checked={method === 'CARD'} onChange={() => setMethod('CARD')} /> Cartão de crédito</label>
                </fieldset>
                {method === 'PIX' && <FakePixQr amount={c.total} />}
                {method === 'CARD' && (
                  <Input label="Número do cartão" inputMode="numeric" autoComplete="off" value={card}
                    onChange={(e) => setCard(e.target.value.replace(/[^\d ]/g, '').slice(0, 23))}
                    hint="Pagamento de demonstração: nenhum valor é cobrado. Cartão terminado em 0002 é recusado, para testar." />
                )}
                <Button type="submit" loading={checkout.isPending}>Pagar {formatMoney(c.total)}</Button>
                <p className="text-xs text-muted">Guardamos só os 4 últimos dígitos do cartão.</p>
              </form>
            </Card>
          </div>
        )}
      </QueryState>
      <p className="mt-4 text-sm"><Link to="/loja" className="text-primary hover:underline">← Continuar comprando</Link></p>
    </>
  );
}

/* ------------------------------------------------------------------------------------------------ pedidos */

export function OrdersPage() {
  return useStorePreview() ? <PreviewOrdersPage /> : <CustomerOrdersPage />;
}

// Como o cliente lê a situação do pedido de peça (o chamado da equipe usa outras palavras).
const partStatus = { OPEN: 'Aguardando a loja', RESOLVED: 'Atendido', CANCELLED: 'Cancelado' } as const;
const partTone = { OPEN: 'warning', RESOLVED: 'success', CANCELLED: 'neutral' } as const;

function CustomerOrdersPage() {
  const orders = useMyOrders();
  const parts = useMyPartRequests();
  return (
    <>
      <PageHeader title="Meus pedidos" subtitle="Tudo o que você comprou, online e na loja."
        help="Pedido confirmado já saiu do estoque. Se a loja cancelar um pedido pago, o pagamento aparece como estornado." />
      <Card>
        <QueryState query={orders} isEmpty={(o) => o.length === 0} empty="Você ainda não fez pedidos.">
          {(list) => (
            <ul className="divide-y divide-border">
              {list.map((o) => (
                <li key={o.id}>
                  <Link to={`/loja/pedidos/${o.id}`} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm hover:bg-surface-2">
                    <span>
                      <strong className="tabular">Pedido nº {o.id}</strong>
                      <span className="block text-xs text-muted">
                        {formatDateTime(o.confirmedAt ?? o.createdAt)} · {o.items} peça(s) · {o.channel === 'ONLINE' ? 'online' : 'na loja'}
                        {o.paymentMethod && ` · ${paymentLabel[o.paymentMethod]}`}
                      </span>
                    </span>
                    <span className="flex items-center gap-2">
                      <span className="tabular">{formatMoney(o.total)}</span>
                      <Badge tone={saleStatusTone[o.status]}>{saleStatusLabel[o.status]}</Badge>
                      {o.paymentStatus === 'REFUNDED' && <Badge tone="info">Estornado</Badge>}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </QueryState>
      </Card>
      {(parts.data?.content.length ?? 0) > 0 && (
        <Card title="Peças que pedi à loja" className="mt-4">
          <ul className="divide-y divide-border">
            {parts.data!.content.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
                <span>
                  <strong>{r.description}</strong>
                  <span className="block text-xs text-muted">
                    {[r.size && `tam. ${r.size}`, r.color, `${r.quantity} un.`, `pedido em ${formatDateTime(r.createdAt)}`].filter(Boolean).join(' · ')}
                  </span>
                  {r.closingNote && <span className="block text-xs text-muted">Loja: {r.closingNote}</span>}
                </span>
                <Badge tone={partTone[r.status]}>{partStatus[r.status]}</Badge>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}

export function OrderDetailPage() {
  const q = useMyOrder(Number(useParams().id));
  return (
    <QueryState query={q}>
      {(o) => (
        <>
          <PageHeader title={`Pedido nº ${o.sale.id}`} subtitle={formatDateTime(o.sale.confirmedAt ?? o.sale.createdAt)}
            help="Os preços são os da hora da compra, mesmo que a loja mude depois." />
          <Card>
            <ul className="divide-y divide-border">
              {o.sale.items.map((i) => (
                <li key={i.id} className="flex justify-between gap-2 px-4 py-2.5 text-sm">
                  <span>{i.productName} <span className="text-xs text-muted">× {i.quantity}</span></span>
                  <span className="tabular">{formatMoney(i.subtotal)}</span>
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border px-4 py-3 text-sm">
              <span className="flex items-center gap-2">
                <Badge tone={saleStatusTone[o.sale.status]}>{saleStatusLabel[o.sale.status]}</Badge>
                {o.paymentMethod && <span className="text-muted">{paymentLabel[o.paymentMethod]}{o.cardLast4 && ` final ${o.cardLast4}`}</span>}
                {o.paymentStatus === 'REFUNDED' && <Badge tone="info">Pagamento estornado</Badge>}
              </span>
              <strong className="tabular">{formatMoney(o.sale.total)}</strong>
            </div>
            {o.sale.cancelReason && <p className="border-t border-border px-4 py-2.5 text-sm text-muted">Cancelado: {o.sale.cancelReason}</p>}
          </Card>
          <p className="mt-4 text-sm"><Link to="/loja/pedidos" className="text-primary hover:underline">← Meus pedidos</Link></p>
        </>
      )}
    </QueryState>
  );
}

/* ------------------------------------------------------------------------------------------------ meus dados */

export function MyDataPage() {
  return useStorePreview() ? <PreviewMyDataPage /> : <CustomerMyDataPage />;
}

function CustomerMyDataPage() {
  const q = useMyData();
  const { saveMyData } = useStoreActions();
  const toast = useToast();
  const [f, setF] = useState<MyDataInput | null>(null);
  useEffect(() => {
    if (q.data && !f) {
      const c = q.data;
      setF({ phone: maskPhone(c.phone), email: c.email, zipCode: maskZip(c.zipCode), street: c.street, number: c.number,
        complement: c.complement, district: c.district, city: c.city, state: c.state });
    }
  }, [q.data, f]);

  async function fillCep(zip: string) {
    const r = await lookupZip(zip).catch(() => null);
    if (r && f) setF({ ...f, street: r.street || f.street, district: r.district || f.district, city: r.city || f.city, state: r.state || f.state });
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!f) return;
    try {
      await saveMyData.mutateAsync(f);
      toast.success('Dados atualizados.');
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <>
      <PageHeader title="Meus dados" subtitle="Contato e endereço."
        help="Você muda telefone, e-mail e endereço. Nome e CPF só a loja muda, porque identificam quem comprou." />
      <QueryState query={q}>
        {(c) => !f ? null : (
          <Card>
            <form onSubmit={save} className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2">
              <Input label="Nome" value={c.fullName} disabled hint="Só a loja muda." />
              <Input label="CPF" value={c.cpf} disabled hint="Só a loja muda." />
              <Input label="Telefone" value={f.phone} onChange={(e) => setF({ ...f, phone: maskPhone(e.target.value) })} />
              <Input label="E-mail" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
              <Input label="CEP" value={f.zipCode} onChange={(e) => setF({ ...f, zipCode: maskZip(e.target.value) })}
                onBlur={(e) => void fillCep(e.target.value)} hint="O endereço é preenchido pelo CEP." />
              <Input label="Rua" value={f.street} onChange={(e) => setF({ ...f, street: e.target.value })} />
              <Input label="Número" value={f.number} onChange={(e) => setF({ ...f, number: e.target.value })} />
              <Input label="Complemento" value={f.complement ?? ''} onChange={(e) => setF({ ...f, complement: e.target.value || null })} />
              <Input label="Bairro" value={f.district} onChange={(e) => setF({ ...f, district: e.target.value })} />
              <Input label="Cidade" value={f.city} onChange={(e) => setF({ ...f, city: e.target.value })} />
              <Input label="UF" value={f.state} maxLength={2} onChange={(e) => setF({ ...f, state: e.target.value.toUpperCase() })} />
              <div className="flex items-end"><Button type="submit" loading={saveMyData.isPending}>Salvar</Button></div>
            </form>
          </Card>
        )}
      </QueryState>
    </>
  );
}
