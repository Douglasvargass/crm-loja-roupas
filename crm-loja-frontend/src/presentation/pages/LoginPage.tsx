import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { homeOf } from '@/domain/permissions';
import { Button } from '../components/atoms/Button';
import { Input } from '../components/atoms/Field';

export function LoginPage() {
  const { state, login } = useSession();
  const navigate = useNavigate();
  const [form, setForm] = useState({ login: '', password: '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (state.status === 'authenticated') {
    return <Navigate to={state.me.mustChangePassword ? '/trocar-senha' : homeOf(state.me.role ?? 'SELLER')} replace />;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form.login.trim() || !form.password) {
      setError('Informe login e senha.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const me = await login(form.login.trim(), form.password);
      navigate(me.mustChangePassword ? '/trocar-senha' : homeOf(me.role ?? 'SELLER'), { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login ou senha incorretos');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-2 text-center">
          <img src="/favicon.svg" alt="" className="size-12" />
          <h1 className="text-xl font-semibold">CRM Loja de Roupas</h1>
          <p className="text-sm text-muted">Entre com seu login para continuar</p>
        </div>
        <form onSubmit={submit} className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 shadow-sm" noValidate>
          <Input
            label="Login (e-mail)"
            type="email"
            autoComplete="username"
            autoFocus
            value={form.login}
            onChange={(e) => setForm({ ...form, login: e.target.value })}
          />
          <Input
            label="Senha"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
          {error && (
            <p role="alert" className="rounded-lg bg-error-soft px-3 py-2 text-sm text-error">
              {error}
            </p>
          )}
          <Button type="submit" loading={busy}>
            Entrar
          </Button>
        </form>
      </div>
    </div>
  );
}
