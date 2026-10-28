import { useState, type FormEvent } from 'react';
import { useToast } from '@/app/context/ToastContext';
import type { CustomerDto, CustomerInput } from '@/data/dto';
import { useSaveCustomer } from '@/data/hooks';
import { ApiError } from '@/data/http';
import { digits, maskCpf, maskPhone, maskZip } from '@/lib/format';
import { lookupZip } from '@/lib/viacep';
import { Button } from '../../components/atoms/Button';
import { Input } from '../../components/atoms/Field';
import { Modal } from '../../components/molecules/Modal';

type Form = Record<keyof CustomerInput, string>;
type Errors = Partial<Record<keyof CustomerInput, string>>;

const EMPTY: Form = {
  fullName: '', cpf: '', phone: '', email: '', zipCode: '', street: '', number: '',
  complement: '', district: '', city: '', state: '',
};

// Código de erro da API → campo que fica destacado.
const FIELD_OF_CODE: Record<string, keyof CustomerInput> = {
  invalid_cpf: 'cpf', cpf_taken: 'cpf', invalid_phone: 'phone', invalid_zip: 'zipCode', invalid_state: 'state',
};

function fromDto(c: CustomerDto): Form {
  return {
    fullName: c.fullName, cpf: maskCpf(c.cpf), phone: maskPhone(c.phone), email: c.email,
    zipCode: maskZip(c.zipCode), street: c.street, number: c.number, complement: c.complement ?? '',
    district: c.district, city: c.city, state: c.state,
  };
}

function validate(f: Form): Errors {
  const e: Errors = {};
  if (f.fullName.trim().length < 3) e.fullName = 'Informe o nome completo do cliente.';
  if (digits(f.cpf).length !== 11) e.cpf = 'Informe os 11 dígitos do CPF.';
  if (digits(f.phone).length < 10) e.phone = 'Informe o telefone com DDD.';
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim())) e.email = 'E-mail inválido.';
  if (digits(f.zipCode).length !== 8) e.zipCode = 'Informe os 8 dígitos do CEP.';
  if (!f.street.trim()) e.street = 'Informe a rua.';
  if (!f.number.trim()) e.number = 'Informe o número.';
  if (!f.district.trim()) e.district = 'Informe o bairro.';
  if (!f.city.trim()) e.city = 'Informe a cidade.';
  if (!/^[A-Za-z]{2}$/.test(f.state.trim())) e.state = 'UF com 2 letras.';
  return e;
}

export function CustomerFormModal({ customer, onClose }: { customer?: CustomerDto; onClose: () => void }) {
  const [form, setForm] = useState<Form>(customer ? fromDto(customer) : EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const save = useSaveCustomer();
  const toast = useToast();

  const set = (k: keyof Form, v: string) => setForm((f) => ({ ...f, [k]: v }));

  async function onZipBlur() {
    const zip = digits(form.zipCode);
    const addr = await lookupZip(zip);
    if (addr) setForm((f) => ({ ...f, street: addr.street || f.street, district: addr.district || f.district, city: addr.city, state: addr.state }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errs = validate(form);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const input: CustomerInput = {
      ...form,
      cpf: digits(form.cpf),
      phone: digits(form.phone),
      zipCode: digits(form.zipCode),
      complement: form.complement.trim() || null,
      state: form.state.trim().toUpperCase(),
    };
    try {
      await save.mutateAsync({ id: customer?.id, input });
      toast.success(customer ? 'Pronto! Os dados do cliente foram atualizados.' : 'Cliente cadastrado com sucesso.');
      onClose();
    } catch (err) {
      const field = err instanceof ApiError && err.code ? FIELD_OF_CODE[err.code] : undefined;
      if (field && err instanceof Error) setErrors({ [field]: err.message });
      else toast.error(err);
    }
  }

  return (
    <Modal
      open
      size="lg"
      title={customer ? 'Editar cliente' : 'Novo cliente'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="customer-form" loading={save.isPending}>Salvar</Button>
        </>
      }
    >
      <form id="customer-form" onSubmit={submit} noValidate className="grid grid-cols-1 gap-3 sm:grid-cols-6">
        <Input className="sm:col-span-6" label="Nome completo" value={form.fullName} error={errors.fullName}
          onChange={(e) => set('fullName', e.target.value)} />
        <Input className="sm:col-span-2" label="CPF" inputMode="numeric" placeholder="000.000.000-00" value={form.cpf}
          error={errors.cpf} onChange={(e) => set('cpf', maskCpf(e.target.value))} />
        <Input className="sm:col-span-2" label="Telefone" inputMode="tel" placeholder="(00) 00000-0000" value={form.phone}
          error={errors.phone} onChange={(e) => set('phone', maskPhone(e.target.value))} />
        <Input className="sm:col-span-2" label="E-mail" type="email" value={form.email} error={errors.email}
          onChange={(e) => set('email', e.target.value)} />
        <Input className="sm:col-span-2" label="CEP" inputMode="numeric" placeholder="00000-000" value={form.zipCode}
          error={errors.zipCode} hint="O endereço é preenchido pelo CEP." onBlur={() => void onZipBlur()}
          onChange={(e) => set('zipCode', maskZip(e.target.value))} />
        <Input className="sm:col-span-3" label="Rua" value={form.street} error={errors.street}
          onChange={(e) => set('street', e.target.value)} />
        <Input className="sm:col-span-1" label="Número" value={form.number} error={errors.number}
          onChange={(e) => set('number', e.target.value)} />
        <Input className="sm:col-span-2" label="Complemento (opcional)" value={form.complement}
          onChange={(e) => set('complement', e.target.value)} />
        <Input className="sm:col-span-2" label="Bairro" value={form.district} error={errors.district}
          onChange={(e) => set('district', e.target.value)} />
        <Input className="sm:col-span-1" label="Cidade" value={form.city} error={errors.city}
          onChange={(e) => set('city', e.target.value)} />
        <Input className="sm:col-span-1" label="UF" maxLength={2} value={form.state} error={errors.state}
          onChange={(e) => set('state', e.target.value.toUpperCase())} />
      </form>
    </Modal>
  );
}
