import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { homeOf } from '@/domain/permissions';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { Input } from '../components/atoms/Field';

// HU33: entrar. Erro sempre com a mesma frase ("Login ou senha incorretos"), para não revelar se o login existe.
// Quem precisa trocar a senha vai primeiro para /trocar-senha; os outros, para a tela inicial do papel.
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
          <h1 className="sr-only">Orden</h1>
          <Logo className="h-36" />
          <p className="text-sm text-muted">Entre com seu login para continuar</p>
        </div>
        <form onSubmit={submit} className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 shadow-sm" noValidate>
          <Input
            label="Login"
            type="text"
            placeholder="nome.sobrenome"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
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
