import { useSyncExternalStore } from 'react';

// Aparência (30/10/2026, acessibilidade): escolhas de cada pessoa, guardadas no navegador dela (localStorage) e
// aplicadas no <html> (data-theme, data-contrast, data-font, data-motion), que o tokens.css transforma em cores,
// tamanho de letra e menos movimento. Vale para a equipe e para a loja do cliente.
// O mesmo cálculo roda uma vez no index.html antes da tela aparecer (para não piscar claro e depois escuro).

export type ThemePref = 'auto' | 'light' | 'dark';
export type FontPref = 'normal' | 'large' | 'xlarge';

export interface Appearance {
  theme: ThemePref;        // auto = segue o modo do computador ou do celular
  font: FontPref;
  contrast: boolean;       // alto contraste
  reduceMotion: boolean;
  libras: boolean;         // tradutor VLibras (ligado por padrão: quem precisa não deve ter de procurar)
}

const KEY = 'orden.aparencia';

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;

// Padrão: claro da marca; menos movimento só se o aparelho já pediu.
const defaults = (): Appearance => ({ theme: 'light', font: 'normal', contrast: false, reduceMotion: prefersReducedMotion(), libras: true });

const oneOf = <T extends string>(v: unknown, allowed: readonly T[], fallback: T): T =>
  typeof v === 'string' && (allowed as readonly string[]).includes(v) ? (v as T) : fallback;

function read(): Appearance {
  const d = defaults();
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return d;
    const p = JSON.parse(raw) as Partial<Appearance>;
    return {
      theme: oneOf(p.theme, ['auto', 'light', 'dark'] as const, d.theme),
      font: oneOf(p.font, ['normal', 'large', 'xlarge'] as const, d.font),
      contrast: typeof p.contrast === 'boolean' ? p.contrast : d.contrast,
      reduceMotion: typeof p.reduceMotion === 'boolean' ? p.reduceMotion : d.reduceMotion,
      libras: typeof p.libras === 'boolean' ? p.libras : d.libras,
    };
  } catch {
    return d;   // sem storage (janela privativa) ou valor corrompido: usa o padrão e a tela funciona igual
  }
}

export const isDark = (a: Appearance) =>
  a.theme === 'dark' || (a.theme === 'auto' && window.matchMedia?.('(prefers-color-scheme: dark)').matches === true);

export function applyToDocument(a: Appearance) {
  const el = document.documentElement;
  el.dataset.theme = isDark(a) ? 'dark' : 'light';
  el.dataset.contrast = a.contrast ? 'high' : 'normal';
  el.dataset.font = a.font;
  el.dataset.motion = a.reduceMotion ? 'reduce' : 'normal';
  // Cor da barra do navegador no celular.
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', isDark(a) ? '#29231f' : '#6b4f3a');
}

let current: Appearance = read();
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());

export function setAppearance(patch: Partial<Appearance>) {
  current = { ...current, ...patch };
  try { localStorage.setItem(KEY, JSON.stringify(current)); } catch { /* sem storage: vale só até fechar a aba */ }
  applyToDocument(current);
  notify();
}

export function resetAppearance() {
  try { localStorage.removeItem(KEY); } catch { /* idem */ }
  current = defaults();
  applyToDocument(current);
  notify();
}

export function useAppearance(): Appearance {
  return useSyncExternalStore(
    (cb) => { listeners.add(cb); return () => { listeners.delete(cb); }; },
    () => current,
    () => current,
  );
}

if (typeof window !== 'undefined') {
  applyToDocument(current);
  // "Automático": quando o computador troca de claro para escuro (ex.: à noite), a tela acompanha.
  window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change', () => {
    if (current.theme === 'auto') { applyToDocument(current); notify(); }
  });
}
