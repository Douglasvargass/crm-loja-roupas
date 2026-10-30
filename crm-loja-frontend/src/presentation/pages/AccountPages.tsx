import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { http } from '@/data/http';
import { maskCpf, maskPhone, maskZip } from '@/lib/format';
import { lookupZip } from '@/lib/viacep';
import { Button } from '../components/atoms/Button';
import { Input } from '../components/atoms/Field';
import { AuthLayout } from './LoginPage';

// "Sou cliente" → Criar conta (RN65, RN66). Depois de criar, já entra e abre a vitrine.
export function SignupPage() {
  const { login } = useSession();
  const navigate = useNavigate();
  const [f, setF] = useState({
    fullName: '', cpf: '', email: '', phone: '', zipCode: '', street: '', number: '', complement: '', district: '',
    city: '', state: '', password: '', confirm: '', acceptTerms: false,
  });
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f, v: string | boolean) => setF((x) => ({ ...x, [k]: v }));

  async function zip(v: string) {
    set('zipCode', maskZip(v));
    const digits = v.replace(/\D/g, '');
    if (digits.length === 8) {
      const a = await lookupZip(digits);
      if (a) setF((x) => ({ ...x, street: a.street || x.street, district: a.district || x.district, city: a.city, state: a.state }));
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (f.fullName.trim().split(/\s+/).length < 2) return setError('Informe o nome completo.');
    if (f.password.length < 8) return setError('A senha precisa ter pelo menos 8 caracteres.');
    if (f.password !== f.confirm) return setError('As duas senhas não são iguais.');
    if (!f.acceptTerms) return setError('Para criar a conta, aceite o uso dos seus dados pela loja.');
    setError(undefined);
    setBusy(true);
    try {
      const { confirm: _c, ...body } = f;
      void _c;
      const r = await http.post<{ login: string }>('/loja/signup', body);
      await login(r.login, f.password);
      navigate('/loja', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível criar a conta.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout wide title="Criar conta" subtitle="Leva um minuto. Com a conta você compra pela loja online e acompanha os seus pedidos.">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-6" noValidate>
        <Input label="Nome completo" className="sm:col-span-6" autoComplete="name" value={f.fullName} onChange={(e) => set('fullName', e.target.value)} />
        <Input label="CPF" className="sm:col-span-3" inputMode="numeric" value={f.cpf} onChange={(e) => set('cpf', maskCpf(e.target.value))} />
        <Input label="Celular" className="sm:col-span-3" inputMode="tel" autoComplete="tel" value={f.phone} onChange={(e) => set('phone', maskPhone(e.target.value))} />
        <Input label="E-mail (vai ser o seu login)" type="email" className="sm:col-span-6" autoComplete="email" value={f.email} onChange={(e) => set('email', e.target.value)} />
        <Input label="CEP" className="sm:col-span-2" inputMode="numeric" autoComplete="postal-code" value={f.zipCode} onChange={(e) => void zip(e.target.value)} />
        <Input label="Rua" className="sm:col-span-4" value={f.street} onChange={(e) => set('street', e.target.value)} />
        <Input label="Número" className="sm:col-span-2" value={f.number} onChange={(e) => set('number', e.target.value)} />
        <Input label="Complemento" className="sm:col-span-4" value={f.complement} onChange={(e) => set('complement', e.target.value)} />
        <Input label="Bairro" className="sm:col-span-2" value={f.district} onChange={(e) => set('district', e.target.value)} />
        <Input label="Cidade" className="sm:col-span-3" value={f.city} onChange={(e) => set('city', e.target.value)} />
        <Input label="UF" className="sm:col-span-1" maxLength={2} value={f.state} onChange={(e) => set('state', e.target.value.toUpperCase())} />
        <Input label="Senha (mínimo 8)" type="password" className="sm:col-span-3" autoComplete="new-password" value={f.password} onChange={(e) => set('password', e.target.value)} />
        <Input label="Repita a senha" type="password" className="sm:col-span-3" autoComplete="new-password" value={f.confirm} onChange={(e) => set('confirm', e.target.value)} />
        <label className="flex items-start gap-2 text-sm sm:col-span-6">
          <input type="checkbox" className="mt-1" checked={f.acceptTerms} onChange={(e) => set('acceptTerms', e.target.checked)} />
          <span>Autorizo a Orden a usar meus dados para atender meus pedidos e falar comigo sobre eles.</span>
        </label>
        {error && <p role="alert" className="rounded-lg bg-error-soft px-3 py-2 text-sm text-error sm:col-span-6">{error}</p>}
        <div className="flex flex-col-reverse gap-3 sm:col-span-6 sm:flex-row sm:items-center sm:justify-between">
          <Link to="/login" className="text-center text-sm text-muted hover:text-text hover:underline">Já tenho conta: entrar</Link>
          <Button type="submit" loading={busy}>Criar conta</Button>
        </div>
      </form>
    </AuthLayout>
  );
}

// Esqueci minha senha (RN67): pede o e-mail. A resposta é a mesma, exista ou não a conta.
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.trim())) return setError('Informe um e-mail válido, ex.: nome@email.com.');
    setError(undefined);
    setBusy(true);
    try {
      const r = await http.post<{ message: string }>('/auth/forgot-password', { email: email.trim() });
      setSent(r.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível enviar agora. Tente de novo.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout title="Esqueci minha senha" subtitle="Informe o e-mail da sua conta. Enviamos um link para você criar uma senha nova.">
      {sent ? (
        <>
          <p role="status" className="rounded-lg bg-success-soft px-3 py-3 text-sm text-success">{sent}</p>
          <p className="text-xs text-muted">O link vale por 30 minutos e funciona uma vez só. Da equipe e sem e-mail cadastrado? Peça ao gerente para resetar a senha em Usuários.</p>
          <Link to="/login" className="text-center text-sm font-medium text-primary hover:underline">Voltar para entrar</Link>
        </>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <Input label="E-mail" type="email" autoComplete="email" autoFocus placeholder="seu@email.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          {error && <p role="alert" className="rounded-lg bg-error-soft px-3 py-2 text-sm text-error">{error}</p>}
          <Button type="submit" loading={busy}>Enviar link</Button>
          <Link to="/login" className="text-center text-sm text-muted hover:text-text hover:underline">Lembrei: voltar para entrar</Link>
        </form>
      )}
    </AuthLayout>
  );
}

// Link do e-mail (RN68): cria a senha nova. O link vale 30 minutos e uma vez só.
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [f, setF] = useState({ password: '', confirm: '' });
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (f.password.length < 8) return setError('A senha precisa ter pelo menos 8 caracteres.');
    if (f.password !== f.confirm) return setError('As duas senhas não são iguais.');
    setError(undefined);
    setBusy(true);
    try {
      await http.post('/auth/reset-password', { token, password: f.password });
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível trocar a senha.');
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Link incompleto" subtitle="Abra o link do e-mail inteiro, ou peça um novo.">
        <Link to="/recuperar-senha" className="text-center text-sm font-medium text-primary hover:underline">Pedir um link novo</Link>
      </AuthLayout>
    );
  }
  return (
    <AuthLayout title="Criar uma senha nova" subtitle="Escolha uma senha de pelo menos 8 caracteres.">
      {done ? (
        <>
          <p role="status" className="rounded-lg bg-success-soft px-3 py-3 text-sm text-success">Senha alterada. Entre com a senha nova.</p>
          <Link to="/login" className="text-center text-sm font-medium text-primary hover:underline">Entrar</Link>
        </>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <Input label="Senha nova" type="password" autoComplete="new-password" autoFocus value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
          <Input label="Repita a senha" type="password" autoComplete="new-password" value={f.confirm} onChange={(e) => setF({ ...f, confirm: e.target.value })} />
          {error && (
            <p role="alert" className="rounded-lg bg-error-soft px-3 py-2 text-sm text-error">
              {error} {error.includes('link') && <Link to="/recuperar-senha" className="font-medium underline">Pedir um novo</Link>}
            </p>
          )}
          <Button type="submit" loading={busy}>Salvar senha</Button>
        </form>
      )}
    </AuthLayout>
  );
}
