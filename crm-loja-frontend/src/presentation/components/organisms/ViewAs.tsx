import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { roleLabel } from '@/data/adapters';
import type { Role, UserRowDto } from '@/data/dto';
import { authApi } from '@/data/hooks';
import { homeOf } from '@/domain/permissions';
import { Button } from '../atoms/Button';
import { Icon } from '../atoms/Icon';
import { Modal } from '../molecules/Modal';

const fold = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
const MANAGER_LIKE: Role[] = ['MANAGER', 'DEVELOPER', 'MARKETING_DEV'];

// Spec crm/10: botão "Ver como" no topo, só para quem pode (gerente, equipe técnica, super admin) e fora do modo.
export function ViewAsButton() {
  const { me, role } = useSession();
  const [open, setOpen] = useState(false);
  if (!me || me.viewAs) return null;
  if (!me.superAdmin && !(role && MANAGER_LIKE.includes(role))) return null;
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        // Em tela estreita (celular, janela pequena) fica só o ícone; antes o botão sumia e não havia outro caminho.
        className="flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-surface-2 hover:text-text"
        title="Ver o sistema como outra pessoa da equipe vê (só para olhar)"
        aria-label="Ver como"
      >
        <Icon name="users" className="size-4" />
        <span className="hidden sm:inline">Ver como</span>
      </button>
      {open && <PickModal onClose={() => setOpen(false)} />}
    </>
  );
}

function PickModal({ onClose }: { onClose: () => void }) {
  const { reloadMe } = useSession();
  const toast = useToast();
  const navigate = useNavigate();
  const targets = useQuery({ queryKey: ['viewAsTargets'], queryFn: authApi.viewAsTargets });
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState<number | null>(null);
  const list = useMemo(
    () => (targets.data ?? []).filter((u) => fold(`${u.fullName} ${u.login}`).includes(fold(q))),
    [targets.data, q],
  );

  async function pick(u: UserRowDto) {
    setBusy(u.id);
    try {
      await authApi.viewAsStart(u.id);
      const me = await reloadMe();
      onClose();
      if (me?.role) navigate(homeOf(me.role), { replace: true });
    } catch (err) {
      toast.error(err);
      setBusy(null);
    }
  }

  return (
    <Modal open title="Ver o sistema como…" onClose={onClose}>
      <p className="mb-3 text-sm text-muted">
        Você vai ver o menu, as telas e os números exatamente como a pessoa vê. É só para olhar: nada pode ser alterado.
        Dura até 30 minutos, e entrar e sair ficam registrados.
      </p>
      {/* Ver o site como o cliente vê, sem escolher um cliente: abre a Prévia da loja (nada é gravado). */}
      <button type="button" onClick={() => { onClose(); navigate('/previa-loja'); }}
        className="mb-3 flex w-full items-center gap-3 rounded-lg border border-primary/40 bg-primary-soft/50 px-3 py-2.5 text-left text-sm hover:bg-primary-soft">
        <Icon name="sales" className="size-5 text-primary" />
        <span>
          <span className="block font-medium">Loja do cliente</span>
          <span className="block text-xs text-muted">Vitrine, carrinho e pagamento como qualquer cliente vê o site. Não é um cliente específico.</span>
        </span>
      </button>
      <input
        aria-label="Buscar pessoa"
        placeholder="Buscar por nome ou login"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        className="mb-3 h-10 w-full rounded-lg border border-border bg-surface px-3 text-sm"
      />
      {targets.isLoading ? (
        <p className="py-6 text-center text-sm text-muted">Carregando…</p>
      ) : list.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">Ninguém encontrado.</p>
      ) : (
        <ul className="max-h-80 divide-y divide-border overflow-y-auto rounded-lg border border-border">
          {list.map((u) => (
            <li key={u.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
              <span className="min-w-0">
                <span className="font-medium">{u.fullName}</span>
                <span className="block text-xs text-muted">{u.login} · {roleLabel[u.role] ?? u.role}</span>
              </span>
              <Button size="sm" variant="secondary" loading={busy === u.id} onClick={() => void pick(u)}>Ver como</Button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}

// Faixa fixa enquanto o modo estiver ligado: deixa claro de quem é a tela e como sair.
export function ViewAsBanner() {
  const { me, reloadMe } = useSession();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  if (!me?.viewAs) return null;

  async function stop() {
    setBusy(true);
    await authApi.viewAsStop().catch(() => undefined);
    const back = await reloadMe();
    setBusy(false);
    if (back?.role) navigate(homeOf(back.role), { replace: true });
  }

  return (
    // Mesmo padrão da faixa do "Ver como" da Plataforma de Operações (olho, saída à direita), em azul claro fora da
    // paleta da marca, para chamar atenção (pedido do Douglas, 29/10).
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-sky-300 bg-sky-100 px-4 py-2.5 text-sm text-sky-900 dark:border-sky-700 dark:bg-sky-950 dark:text-sky-100" role="status">
      <span className="flex items-center gap-2">
        <Icon name="eye" className="size-4 shrink-0" />
        <span>Vendo como <strong>{me.fullName}</strong> ({me.role ? roleLabel[me.role] : 'sem papel'}) · somente leitura</span>
      </span>
      <button type="button" disabled={busy} onClick={() => void stop()}
        className="shrink-0 rounded-lg border border-sky-700/50 bg-white/70 px-3 py-1 text-xs font-semibold text-sky-900 hover:bg-white dark:border-sky-400 dark:bg-sky-900 dark:text-sky-50 dark:hover:bg-sky-800 disabled:opacity-60">
        {busy ? 'Saindo…' : `Sair do modo · voltar a ser ${me.viewAs.viewerName.split(' ')[0]}`}
      </button>
    </div>
  );
}
