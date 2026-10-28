import { useState, type FormEvent } from 'react';
import { useToast } from '@/app/context/ToastContext';
import { useCategories, useSaveProduct } from '@/data/hooks';
import { ApiError } from '@/data/http';
import type { ProductDto, ProductInput } from '@/data/dto';
import { Button } from '../../components/atoms/Button';
import { Input, Select, Textarea } from '../../components/atoms/Field';
import { Modal } from '../../components/molecules/Modal';

type Form = { code: string; name: string; description: string; categoryId: string; size: string; color: string; price: string; initialStock: string; minStock: string };
type Errors = Partial<Record<keyof Form, string>>;

const FIELD_OF_CODE: Record<string, keyof Form> = { product_code_taken: 'code', category_inactive: 'categoryId' };

// Aceita "1.249,90", "49,90" e "49.90".
const parseMoney = (s: string) => {
  const t = s.trim();
  return Number(t.includes(',') ? t.replace(/\./g, '').replace(',', '.') : t);
};
const isInt = (s: string) => /^\d+$/.test(s.trim());

export function ProductFormModal({ product, onClose }: { product?: ProductDto; onClose: () => void }) {
  const categories = useCategories();
  const save = useSaveProduct();
  const toast = useToast();
  const [form, setForm] = useState<Form>({
    code: product?.code ?? '',
    name: product?.name ?? '',
    description: product?.description ?? '',
    categoryId: product ? String(product.categoryId) : '',
    size: product?.size ?? '',
    color: product?.color ?? '',
    price: product ? product.price.toFixed(2).replace('.', ',') : '',
    initialStock: '0',
    minStock: product ? String(product.minStock) : '5',
  });
  const [errors, setErrors] = useState<Errors>({});
  const set = (k: keyof Form, v: string) => setForm((f) => ({ ...f, [k]: v }));

  function validate(): Errors {
    const e: Errors = {};
    if (!form.code.trim()) e.code = 'Informe o código do produto.';
    if (!form.name.trim()) e.name = 'Informe o nome do produto.';
    if (!form.categoryId) e.categoryId = 'Escolha a categoria.';
    if (!form.size.trim()) e.size = 'Informe o tamanho.';
    if (!form.color.trim()) e.color = 'Informe a cor.';
    const price = parseMoney(form.price);
    if (!form.price.trim() || Number.isNaN(price)) e.price = 'Informe o preço de venda.';
    else if (price <= 0) e.price = 'O preço deve ser maior que zero.';
    if (!product && !isInt(form.initialStock)) e.initialStock = 'Use um número inteiro, zero ou maior.';
    if (!isInt(form.minStock)) e.minStock = 'Use um número inteiro, zero ou maior.';
    return e;
  }

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    const errs = validate();
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const input: ProductInput = {
      code: form.code.trim().toUpperCase(),
      name: form.name.trim(),
      description: form.description.trim() || null,
      categoryId: Number(form.categoryId),
      size: form.size.trim().toUpperCase(),
      color: form.color.trim(),
      price: parseMoney(form.price),
      initialStock: product ? 0 : Number(form.initialStock),
      minStock: Number(form.minStock),
    };
    try {
      await save.mutateAsync({ id: product?.id, input });
      toast.success(product ? 'Produto atualizado.' : 'Produto cadastrado.');
      onClose();
    } catch (err) {
      const field = err instanceof ApiError && err.code ? FIELD_OF_CODE[err.code] : undefined;
      if (field && err instanceof Error) setErrors({ [field]: err.message });
      else toast.error(err);
    }
  }

  // Categoria inativa só aparece se já for a do produto em edição (HU08).
  const options = (categories.data ?? []).filter((c) => c.active || String(c.id) === form.categoryId);

  return (
    <Modal
      open
      size="lg"
      title={product ? 'Editar produto' : 'Novo produto'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="product-form" loading={save.isPending}>Salvar</Button>
        </>
      }
    >
      <form id="product-form" onSubmit={submit} noValidate className="grid grid-cols-1 gap-3 sm:grid-cols-6">
        <Input className="sm:col-span-2" label="Código" placeholder="CAM-001-M-BR" value={form.code} error={errors.code}
          onChange={(e) => set('code', e.target.value)} />
        <Input className="sm:col-span-4" label="Nome" value={form.name} error={errors.name} onChange={(e) => set('name', e.target.value)} />
        <Select className="sm:col-span-2" label="Categoria" value={form.categoryId} error={errors.categoryId}
          onChange={(e) => set('categoryId', e.target.value)}>
          <option value="">Escolha…</option>
          {options.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
        <Input className="sm:col-span-2" label="Tamanho" placeholder="P, M, G, 40…" value={form.size} error={errors.size}
          onChange={(e) => set('size', e.target.value)} />
        <Input className="sm:col-span-2" label="Cor" value={form.color} error={errors.color} onChange={(e) => set('color', e.target.value)} />
        <Input className="sm:col-span-2" label="Preço de venda (R$)" inputMode="decimal" placeholder="0,00" value={form.price}
          error={errors.price} onChange={(e) => set('price', e.target.value)} />
        {product ? (
          <Input className="sm:col-span-2" label="Estoque atual" value={String(product.stockQty)} disabled
            hint="Muda só por entrada, ajuste ou venda." />
        ) : (
          <Input className="sm:col-span-2" label="Estoque inicial" inputMode="numeric" value={form.initialStock}
            error={errors.initialStock} onChange={(e) => set('initialStock', e.target.value)} />
        )}
        <Input className="sm:col-span-2" label="Estoque mínimo" inputMode="numeric" value={form.minStock} error={errors.minStock}
          hint="Abaixo disso o produto aparece como estoque baixo." onChange={(e) => set('minStock', e.target.value)} />
        <Textarea className="sm:col-span-6" label="Descrição (opcional)" value={form.description}
          onChange={(e) => set('description', e.target.value)} />
        {product && (
          <p className="text-xs text-muted sm:col-span-6">Mudar o preço não altera vendas já registradas.</p>
        )}
      </form>
    </Modal>
  );
}
