import { useEffect } from 'react';
import { useAppearance } from '@/lib/appearance';

// VLibras (30/10/2026, acessibilidade): tradutor de texto para Libras do governo federal (gratuito, código aberto).
// A pessoa seleciona um texto e o boneco o traduz. Carregamos a versão FIXA 7.12.2 do plugin (o endereço oficial
// vlibras.gov.br/app/vlibras-plugin.js só redireciona para ela): uma versão que nunca muda sozinha é mais segura que
// um script que o governo possa trocar sem avisar. Só carrega se a pessoa deixar ligado em "Aparência", e uma falha
// (sem internet, servidor fora do ar) é silenciosa: o sistema segue normal, só sem o boneco.
const SCRIPT = 'https://cdn.jsdelivr.net/gh/spbgovbr-vlibras/vlibras-portal@v7.12.2/app/vlibras-plugin.js';
const APP = 'https://vlibras.gov.br/app';

interface VLibrasWindow extends Window {
  VLibras?: { Widget: new (url: string) => unknown };
}

let loading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if ((window as VLibrasWindow).VLibras) return Promise.resolve();
  loading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script');
    s.src = SCRIPT;
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => { loading = null; reject(new Error('VLibras indisponível')); };
    document.body.appendChild(s);
  });
  return loading;
}

// As marcações abaixo (atributos vw) são as que o plugin procura para se montar; ele escreve dentro delas.
const MARK: Record<string, string> = { vw: '' };
const BUTTON: Record<string, string> = { 'vw-access-button': '' };
const WRAPPER: Record<string, string> = { 'vw-plugin-wrapper': '' };

export function VLibras() {
  const { libras } = useAppearance();

  useEffect(() => {
    if (!libras) return;
    let cancelled = false;
    loadScript()
      .then(() => {
        if (cancelled) return;
        const W = (window as VLibrasWindow).VLibras;
        if (W) new W.Widget(APP);
      })
      .catch(() => { /* sem internet ou bloqueado: segue sem o tradutor */ });
    return () => { cancelled = true; };
  }, [libras]);

  if (!libras) return null;
  return (
    <div {...MARK} className="enabled">
      <div {...BUTTON} className="active" />
      <div {...WRAPPER}>
        <div className="vw-plugin-top-wrapper" />
      </div>
    </div>
  );
}
