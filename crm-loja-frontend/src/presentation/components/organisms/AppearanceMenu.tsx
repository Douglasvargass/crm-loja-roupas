import { useState, type ReactNode } from 'react';
import { resetAppearance, setAppearance, useAppearance, type FontPref, type ThemePref } from '@/lib/appearance';
import { cn } from '@/lib/cn';
import { Button } from '../atoms/Button';
import { Icon } from '../atoms/Icon';
import { Modal } from '../molecules/Modal';

// Botão "Aparência" do topo (30/10/2026, acessibilidade): tema claro/escuro, tamanho da letra, alto contraste, menos
// movimento e Libras. Fica em todas as telas (equipe, loja e login). A escolha é da pessoa e fica no navegador dela.
export function AppearanceButton() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        aria-label="Aparência: tema claro ou escuro, tamanho da letra, contraste e Libras"
        title="Aparência: tema, letra, contraste e Libras"
        className="flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-surface-2 hover:text-text"
      >
        <Icon name="appearance" className="size-4" />
        <span className="hidden lg:inline">Aparência</span>
      </button>
      {open && <AppearanceDialog onClose={() => setOpen(false)} />}
    </>
  );
}

const THEMES: { id: ThemePref; label: string; hint: string }[] = [
  { id: 'light', label: 'Claro', hint: 'Cores da marca, fundo claro' },
  { id: 'dark', label: 'Escuro', hint: 'Fundo café, menos brilho' },
  { id: 'auto', label: 'Automático', hint: 'Segue o computador ou o celular' },
];

const FONTS: { id: FontPref; label: string; sample: string }[] = [
  { id: 'normal', label: 'Normal', sample: 'text-sm' },
  { id: 'large', label: 'Grande', sample: 'text-base' },
  { id: 'xlarge', label: 'Maior', sample: 'text-xl' },
];

function AppearanceDialog({ onClose }: { onClose: () => void }) {
  const a = useAppearance();
  return (
    <Modal
      open
      title="Aparência"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={resetAppearance}>Restaurar o padrão</Button>
          <Button onClick={onClose}>Pronto</Button>
        </>
      }
    >
      <p className="mb-4 text-sm text-muted">Escolha como você prefere ver o sistema. A mudança vale na hora e fica salva neste navegador.</p>

      <fieldset className="mb-5">
        <legend className="mb-2 text-sm font-semibold">Tema</legend>
        <div className="grid gap-2 sm:grid-cols-3">
          {THEMES.map((t) => (
            <label key={t.id} className={cn('flex cursor-pointer flex-col gap-0.5 rounded-lg border px-3 py-2 text-sm has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary',
              a.theme === t.id ? 'border-primary bg-primary-soft' : 'border-border-input bg-surface hover:bg-surface-2')}>
              <span className="flex items-center gap-2 font-medium">
                <input type="radio" name="tema" className="accent-primary" checked={a.theme === t.id} onChange={() => setAppearance({ theme: t.id })} />
                {t.label}
              </span>
              <span className="text-xs text-muted">{t.hint}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="mb-5">
        <legend className="mb-2 text-sm font-semibold">Tamanho da letra</legend>
        <div className="grid grid-cols-3 gap-2">
          {FONTS.map((f) => (
            <label key={f.id} className={cn('flex cursor-pointer flex-col items-center gap-0.5 rounded-lg border px-3 py-2 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary',
              a.font === f.id ? 'border-primary bg-primary-soft' : 'border-border-input bg-surface hover:bg-surface-2')}>
              <input type="radio" name="letra" className="sr-only" checked={a.font === f.id} onChange={() => setAppearance({ font: f.id })} />
              <span className={cn('font-semibold', f.sample)} aria-hidden>Aa</span>
              <span className="text-xs">{f.label}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <div className="flex flex-col gap-3">
        <Switch label="Alto contraste" hint="Texto mais forte, bordas marcadas e links sublinhados." checked={a.contrast}
          onChange={(v) => setAppearance({ contrast: v })} />
        <Switch label="Reduzir movimento" hint="Desliga as animações do menu, da Talita e das janelas." checked={a.reduceMotion}
          onChange={(v) => setAppearance({ reduceMotion: v })} />
        <Switch label="Tradutor de Libras (VLibras)" checked={a.libras} onChange={(v) => setAppearance({ libras: v })}
          hint={<>Mostra o tradutor oficial do governo federal. Ao selecionar um texto, o boneco o traduz para Libras. O texto selecionado é enviado ao servidor do VLibras para ser traduzido. A tradução é automática e pode errar.</>} />
      </div>
    </Modal>
  );
}

function Switch({ label, hint, checked, onChange }: { label: string; hint: ReactNode; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-border-input bg-surface px-3 py-2 text-sm hover:bg-surface-2 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary">
      <input type="checkbox" role="switch" className="mt-1 size-4 shrink-0 accent-primary" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span>
        <span className="block font-medium">{label}</span>
        <span className="block text-xs text-muted">{hint}</span>
      </span>
    </label>
  );
}
