import type { ReactNode } from 'react';

// Markdown mínimo para as respostas do assistente: parágrafos, **negrito**, listas e tabelas.
// Monta elementos React (nada de innerHTML), então texto vindo do modelo nunca vira HTML executável.

function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? <strong key={i}>{part.slice(2, -2)}</strong> : part,
  );
}

const cells = (row: string) => row.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());

export function Markdown({ text }: { text: string }) {
  const lines = text.split('\n');
  const out: ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i] ?? '';
    if (line.trim().startsWith('|') && (lines[i + 1] ?? '').includes('---')) {
      const header = cells(line);
      const rows: string[][] = [];
      i += 2;
      while (i < lines.length && (lines[i] ?? '').trim().startsWith('|')) rows.push(cells(lines[i++] ?? ''));
      out.push(
        <div key={out.length} className="my-2 overflow-x-auto">
          <table className="text-sm">
            <thead>
              <tr>{header.map((h, j) => <th key={j} className="border-b border-border px-2 py-1 text-left font-semibold">{inline(h)}</th>)}</tr>
            </thead>
            <tbody>
              {rows.map((r, k) => (
                <tr key={k}>{r.map((c, j) => <td key={j} className="border-b border-border px-2 py-1">{inline(c)}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }
    if (/^\s*([-*]|\d+\.)\s+/.test(line)) {
      const ordered = /^\s*\d+\./.test(line);
      const items: string[] = [];
      while (i < lines.length && /^\s*([-*]|\d+\.)\s+/.test(lines[i] ?? '')) {
        items.push((lines[i++] ?? '').replace(/^\s*([-*]|\d+\.)\s+/, ''));
      }
      const List = ordered ? 'ol' : 'ul';
      out.push(
        <List key={out.length} className={`my-1 ml-5 ${ordered ? 'list-decimal' : 'list-disc'}`}>
          {items.map((it, k) => <li key={k}>{inline(it)}</li>)}
        </List>,
      );
      continue;
    }
    if (/^#{1,4}\s/.test(line)) {
      out.push(<p key={out.length} className="mt-2 font-semibold">{inline(line.replace(/^#+\s/, ''))}</p>);
    } else if (line.trim()) {
      out.push(<p key={out.length} className="my-1">{inline(line)}</p>);
    }
    i++;
  }
  return <div className="text-sm leading-relaxed">{out}</div>;
}
