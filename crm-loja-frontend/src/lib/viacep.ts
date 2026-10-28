// Preenche o endereço a partir do CEP (ViaCEP, serviço público). Falha silenciosa: o usuário digita à mão.
export interface Address {
  street: string;
  district: string;
  city: string;
  state: string;
}

export async function lookupZip(zip: string): Promise<Address | null> {
  if (!/^\d{8}$/.test(zip)) return null;
  try {
    const res = await fetch(`https://viacep.com.br/ws/${zip}/json/`);
    if (!res.ok) return null;
    const data = (await res.json()) as {
      erro?: boolean;
      logradouro: string;
      bairro: string;
      localidade: string;
      uf: string;
    };
    if (data.erro) return null;
    return { street: data.logradouro, district: data.bairro, city: data.localidade, state: data.uf };
  } catch {
    return null;
  }
}
