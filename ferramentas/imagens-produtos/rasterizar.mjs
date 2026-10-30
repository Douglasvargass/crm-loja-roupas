import { chromium } from 'playwright-core';
import fs from 'node:fs';
import path from 'node:path';
const [src, out] = process.argv.slice(2);
const b = await chromium.launch({ channel: 'chrome', headless: true });
const p = await b.newPage({ viewport: { width: 1000, height: 1000 }, deviceScaleFactor: 1.2 });
for (const slug of fs.readdirSync(src)) {
  fs.mkdirSync(path.join(out, slug), { recursive: true });
  for (const f of fs.readdirSync(path.join(src, slug))) {
    const svg = fs.readFileSync(path.join(src, slug, f), 'utf8');
    await p.setContent(`<html><body style="margin:0">${svg}</body></html>`);
    await p.locator('svg').first().screenshot({ path: path.join(out, slug, f.replace('.svg', '.jpg')), type: 'jpeg', quality: 86 });
  }
}
await b.close();
console.log('jpg ok');
