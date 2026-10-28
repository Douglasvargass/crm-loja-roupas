import { useState, type FormEvent } from 'react';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { roleLabel } from '@/data/adapters';
import { useUserActions, useUsers } from '@/data/hooks';
import type { Role, UserRowDto } from '@/data/dto';
import { formatDateTime } from '@/lib/format';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Select } from '../components/atoms/Field';
import { Icon } from '../components/atoms/Icon';
import { Modal } from '../components/molecules/Modal';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

const ROLES = Object.keys(roleLabel) as Role[];

export function UsersPage() {
  const { me } = useSession();
  const query = useUsers();
  const { update } = useUserActions();
  const toast = useToast();
  const [creating, setCreating] = useState(false);

  async function change(u: UserRowDto, patch: { role?: Role; active?: boolean }) {
    try {
      await update.mutateAsync({ id: u.id, ...patch });
      toast.success('Conta atualizada. A mudança foi registrada na auditoria.');
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <>
      <PageHeader
        title="Usuários"
        subtitle="Contas da equipe e o papel de cada pessoa."
        actions={<Button onClick={() => setCreating(true)}><Icon name="plus" className="size-4" />Nova conta</Button>}
      />
      <Card>
        <QueryState query={query} isEmpty={(d) => d.length === 0} empty="Nenhuma conta com acesso ao CRM.">
          {(rows) => (
            <DataTable<UserRowDto>
              rows={rows}
              rowKey={(u) => u.id}
              columns={[
                { header: 'Nome', cell: (u) => <span className="font-medium">{u.fullName}</span> },
                { header: 'Login', cell: (u) => u.login },
                {
                  header: 'Papel',
                  cell: (u) => (
                    <select
                      aria-label={`Papel de ${u.fullName}`}
                      value={u.role}
                      disabled={u.id === me?.id || update.isPending}
                      onChange={(e) => void change(u, { role: e.target.value as Role })}
                      className="h-8 rounded-md border border-border bg-surface px-2 text-sm disabled:opacity-60"
                    >
                      {ROLES.map((r) => <option key={r} value={r}>{roleLabel[r]}</option>)}
                    </select>
                  ),
                },
                {
                  header: 'Situação',
                  cell: (u) => (
                    <span className="flex gap-1">
                      <Badge tone={u.active ? 'success' : 'neutral'}>{u.active ? 'Ativa' : 'Inativa'}</Badge>
                      {u.mustChangePassword && <Badge tone="warning">Troca de senha pendente</Badge>}
                    </span>
                  ),
                },
                { header: 'Último acesso', cell: (u) => formatDateTime(u.lastLoginAt) },
                {
                  header: 'Ações',
                  align: 'right',
                  cell: (u) => u.id === me?.id ? <span className="text-xs text-muted">você</span> : (
                    <Button size="sm" variant="ghost" disabled={update.isPending} onClick={() => void change(u, { active: !u.active })}>
                      {u.active ? 'Desativar' : 'Reativar'}
                    </Button>
                  ),
                },
              ]}
            />
          )}
        </QueryState>
      </Card>
      {creating && <CreateUserModal onClose={() => setCreating(false)} />}
    </>
  );
}

function CreateUserModal({ onClose }: { onClose: () => void }) {
  const { create } = useUserActions();
  const [form, setForm] = useState({ login: '', fullName: '', role: 'SELLER' as Role });
  const [error, setError] = useState<string>();
  const [created, setCreated] = useState<{ login: string; password: string } | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.login) || form.fullName.trim().length < 3) {
      setError('Informe um e-mail válido como login e o nome completo.');
      return;
    }
    try {
      const r = await create.mutateAsync(form);
      setCreated({ login: r.user.login, password: r.provisionalPassword });
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  if (created) {
    return (
      <Modal open title="Conta criada" onClose={onClose} footer={<Button onClick={onClose}>Pronto</Button>}>
        <p className="text-sm">Passe estes dados para a pessoa. A senha provisória aparece <strong>só agora</strong>; no primeiro acesso ela escolhe uma nova.</p>
        <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 rounded-lg bg-surface-2 p-3 text-sm">
          <dt className="text-muted">Login</dt><dd className="font-medium">{created.login}</dd>
          <dt className="text-muted">Senha</dt><dd className="font-mono font-semibold">{created.password}</dd>
        </dl>
      </Modal>
    );
  }

  return (
    <Modal
      open
      title="Nova conta"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="user-form" loading={create.isPending}>Criar conta</Button>
        </>
      }
    >
      <form id="user-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        <Input label="Nome completo" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
        <Input label="Login (e-mail)" type="email" value={form.login} onChange={(e) => setForm({ ...form, login: e.target.value })} />
        <Select label="Papel" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
          {ROLES.map((r) => <option key={r} value={r}>{roleLabel[r]}</option>)}
        </Select>
        {error && <p className="text-sm text-error">{error}</p>}
      </form>
    </Modal>
  );
}
