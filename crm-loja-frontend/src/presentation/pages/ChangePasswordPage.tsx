import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { authApi } from '@/data/hooks';
import { homeOf } from '@/domain/permissions';
import { Button } from '../components/atoms/Button';
import { Input } from '../components/atoms/Field';

export function ChangePasswordPage() {
  const { me, setMe, logout } = useSession();
  const toast = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!form.current) errs.current = 'Informe a senha atual.';
    if (form.next.length < 8) errs.next = 'A nova senha precisa ter pelo menos 8 caracteres.';
    else if (form.next === form.current) errs.next = 'A nova senha precisa ser diferente da atual.';
    if (form.confirm !== form.next) errs.confirm = 'As senhas não conferem.';
    setErrors(errs);
    if (Object.keys(errs).length) return;

    setBusy(true);
    try {
      const updated = await authApi.changePassword(form.current, form.next);
      setMe(updated);
      toast.success('Senha alterada. As sessões em outros navegadores foram encerradas.');
      navigate(homeOf(updated.role ?? 'SELLER'), { replace: true });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Não foi possível trocar a senha.';
      setErrors(msg.includes('atual') ? { current: msg } : { next: msg });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <form onSubmit={submit} noValidate className="flex w-full max-w-sm flex-col gap-4 rounded-xl border border-border bg-surface p-6">
        <div>
          <h1 className="text-lg font-semibold">Trocar senha</h1>
          {me?.mustChangePassword && (
            <p className="mt-1 text-sm text-muted">Este é seu primeiro acesso: escolha uma senha só sua para continuar.</p>
          )}
        </div>
        <Input label="Senha atual" type="password" autoComplete="current-password" value={form.current}
          error={errors.current} onChange={(e) => setForm({ ...form, current: e.target.value })} />
        <Input label="Nova senha" type="password" autoComplete="new-password" value={form.next} hint="Mínimo de 8 caracteres."
          error={errors.next} onChange={(e) => setForm({ ...form, next: e.target.value })} />
        <Input label="Repita a nova senha" type="password" autoComplete="new-password" value={form.confirm}
          error={errors.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
        <Button type="submit" loading={busy}>Salvar nova senha</Button>
        {me?.mustChangePassword
          ? <Button variant="ghost" onClick={() => void logout()}>Sair</Button>
          : <Button variant="ghost" onClick={() => navigate(-1)}>Voltar</Button>}
      </form>
    </div>
  );
}
