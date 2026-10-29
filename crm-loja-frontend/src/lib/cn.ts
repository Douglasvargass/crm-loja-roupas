// Junta classes CSS ignorando as condições falsas: cn("botão", ativo && "destaque").
export function cn(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(' ');
}
