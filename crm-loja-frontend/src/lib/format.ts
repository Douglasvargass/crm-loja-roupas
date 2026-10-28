// Formatação e máscaras. A API recebe e devolve só dígitos; a tela mostra formatado.

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });

export const digits = (s: string) => s.replace(/\D/g, '');

export const formatMoney = (v: number) => money.format(v);

export function formatDateTime(iso: string | null | undefined) {
  return iso ? dateTime.format(new Date(iso)) : '—';
}

// "2026-10-27" vem sem hora: montar a data no fuso local evita voltar um dia.
export function formatDate(isoDate: string) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return dateOnly.format(new Date(y ?? 0, (m ?? 1) - 1, d ?? 1));
}

export function maskCpf(v: string) {
  return digits(v)
    .slice(0, 11)
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
}

export function maskPhone(v: string) {
  const d = digits(v).slice(0, 11);
  if (d.length <= 10) return d.replace(/(\d{2})(\d)/, '($1) $2').replace(/(\d{4})(\d)/, '$1-$2');
  return d.replace(/(\d{2})(\d)/, '($1) $2').replace(/(\d{5})(\d)/, '$1-$2');
}

export function maskZip(v: string) {
  return digits(v).slice(0, 8).replace(/(\d{5})(\d)/, '$1-$2');
}

// Data local no formato da API (yyyy-mm-dd), sem passar por UTC.
export function toIsoDate(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return current === 0 ? 0 : null;
  return ((current - previous) / previous) * 100;
}
