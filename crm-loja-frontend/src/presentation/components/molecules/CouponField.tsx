import { useState } from 'react';
import { useToast } from '@/app/context/ToastContext';
import { formatMoney } from '@/lib/format';
import { Button } from '../atoms/Button';

// Cupom de desconto (RN61 a RN64): digitar o código e aplicar; aplicado, mostra o desconto e o botão Tirar.
// Usado na venda do balcão e no carrinho da loja online.
export function CouponField({ code, discount, onApply, onRemove, busy, disabled }: {
  code: string | null;
  discount: number;
  onApply: (code: string) => Promise<unknown>;
  onRemove: () => Promise<unknown>;
  busy?: boolean;
  disabled?: boolean;
}) {
  const toast = useToast();
  const [value, setValue] = useState('');
  const [error, setError] = useState<string>();

  // Sem <form> próprio: no carrinho o campo fica dentro do formulário de pagamento, e formulário dentro de
  // formulário não funciona. Enter e o botão aplicam o cupom sem enviar o pagamento.
  async function submit() {
    if (!value.trim()) {
      setError('Digite o código do cupom.');
      return;
    }
    setError(undefined);
    try {
      await onApply(value.trim());
      setValue('');
      toast.success('Cupom aplicado.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível aplicar o cupom.');
    }
  }

  if (code) {
    return (
      <div className="flex items-center justify-between gap-2 rounded-lg border border-success/30 bg-success-soft px-3 py-2 text-sm">
        <span>
          Cupom <strong className="font-mono">{code}</strong>
          {discount > 0 ? <> · <span className="tabular">−{formatMoney(discount)}</span></> : ' · abaixo do valor mínimo'}
        </span>
        <Button size="sm" variant="ghost" type="button" loading={busy} disabled={disabled}
          onClick={async () => { try { await onRemove(); } catch (err) { toast.error(err); } }}>
          Tirar
        </Button>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-1">
      <span className="flex gap-2">
        <input aria-label="Código do cupom" placeholder="Tem cupom? Digite aqui" value={value} disabled={disabled}
          onChange={(e) => setValue(e.target.value.toUpperCase())}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); e.stopPropagation(); void submit(); } }}
          className="h-9 min-w-0 flex-1 rounded-md border border-border bg-surface px-3 font-mono text-sm uppercase placeholder:font-sans placeholder:normal-case" />
        <Button size="sm" variant="secondary" type="button" loading={busy} disabled={disabled} onClick={() => void submit()}>Aplicar</Button>
      </span>
      {error && <span role="alert" className="text-xs text-error">{error}</span>}
    </div>
  );
}

// Linhas de subtotal e desconto acima do total (só aparecem quando há desconto).
export function DiscountLines({ subtotal, discount }: { subtotal: number; discount: number }) {
  if (!discount) return null;
  return (
    <>
      <span className="flex justify-between text-sm text-muted"><span>Peças</span><span className="tabular">{formatMoney(subtotal)}</span></span>
      <span className="flex justify-between text-sm text-success"><span>Desconto do cupom</span><span className="tabular">−{formatMoney(discount)}</span></span>
    </>
  );
}
