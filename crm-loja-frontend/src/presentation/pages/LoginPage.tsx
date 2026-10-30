import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { homeOf } from '@/domain/permissions';
import { cn } from '@/lib/cn';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { Input } from '../components/atoms/Field';

type Who = 'team' | 'customer';

// HU33: entrar. Erro sempre com a mesma frase ("Login ou senha incorretos"), para não revelar se o login existe.
// Duas portas: "Sou da equipe" (login nome.sobrenome) e "Sou cliente" (e-mail), com Criar conta e Esqueci minha senha.
export function LoginPage() {
  const { state, login } = useSession();
  const navigate = useNavigate();
  const [who, setWho] = useState<Who>(() => {
    try { return (localStorage.getItem('orden.login.who') as Who) || 'team'; } catch { return 'team'; }
  });
  const [form, setForm] = useState({ login: '', password: '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (state.status === 'authenticated') {
    return <Navigate to={state.me.mustChangePassword ? '/trocar-senha' : homeOf(state.me.role ?? 'SELLER')} replace />;
  }

  function choose(w: Who) {
    setWho(w);
    setError(null);
    try { localStorage.setItem('orden.login.who', w); } catch { /* ignora */ }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form.login.trim() || !form.password) {
      setError(who === 'customer' ? 'Informe e-mail e senha.' : 'Informe login e senha.');
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
    <AuthLayout
      title="Bem-vindo(a) à Orden"
      subtitle={who === 'customer' ? 'Que bom ter você aqui. Entre para ver a vitrine e os seus pedidos.' : 'Bom trabalho hoje! Entre com o seu login da equipe.'}
    >
      <div role="tablist" aria-label="Quem está entrando" className="grid grid-cols-2 gap-1 rounded-lg bg-surface-2 p-1">
        {(['team', 'customer'] as Who[]).map((w) => (
          <button key={w} type="button" role="tab" aria-selected={who === w} onClick={() => choose(w)}
            className={cn('rounded-md px-3 py-2 text-sm font-medium transition-colors', who === w ? 'bg-surface shadow-sm' : 'text-muted hover:text-text')}>
            {w === 'team' ? 'Sou da equipe' : 'Sou cliente'}
          </button>
        ))}
      </div>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <Input
          label={who === 'customer' ? 'E-mail' : 'Login'}
          type={who === 'customer' ? 'email' : 'text'}
          placeholder={who === 'customer' ? 'seu@email.com' : 'nome.sobrenome'}
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
        {error && <p role="alert" className="rounded-lg bg-error-soft px-3 py-2 text-sm text-error">{error}</p>}
        <Button type="submit" loading={busy}>Entrar</Button>
        <Link to="/recuperar-senha" className="text-center text-sm text-muted hover:text-text hover:underline">Esqueci minha senha</Link>
      </form>
      {who === 'customer' ? (
        <p className="border-t border-border pt-4 text-center text-sm">
          Ainda não tem conta? <Link to="/cadastro" className="font-semibold text-primary hover:underline">Criar conta</Link>
        </p>
      ) : (
        <p className="border-t border-border pt-4 text-center text-xs text-muted">
          Primeiro acesso? Use a senha provisória que o gerente passou: o sistema pede uma nova.
        </p>
      )}
    </AuthLayout>
  );
}

// Moldura das telas sem login (entrar, criar conta, recuperar senha).
export function AuthLayout({ title, subtitle, children, wide }: { title: string; subtitle?: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className={cn('w-full', wide ? 'max-w-2xl' : 'max-w-sm')}>
        <div className="mb-6 flex flex-col items-center gap-2 text-center">
          <Logo className="h-28" />
          <h1 className="mt-2 text-2xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="max-w-sm text-sm text-muted">{subtitle}</p>}
        </div>
        <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 shadow-sm">{children}</div>
      </div>
    </div>
  );
}
