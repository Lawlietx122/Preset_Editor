// Regenerates PWA icons and Android launcher/splash images from src/icons/gear.svg.
// Usage: node tools/make-icons.mjs   (needs the "playwright" dev dependency)
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const svg = fs.readFileSync(path.join(here, 'src/icons/gear.svg'), 'utf8');
const BG = 'linear-gradient(145deg,#1d2433,#101010)';

const browser = await chromium.launch();
async function render(file, width, height, { glyph = 0.64, round = false, transparent = false } = {}) {
  const page = await browser.newPage({ viewport: { width, height } });
  const size = Math.min(width, height) * glyph;
  await page.setContent(`<html><body style="margin:0;background:transparent">
    <div style="width:${width}px;height:${height}px;display:flex;align-items:center;justify-content:center;
      background:${transparent ? 'transparent' : BG};border-radius:${round ? '50%' : '0'}">
      <div style="width:${size}px;height:${size}px">${svg.replace('<svg ', '<svg width="100%" height="100%" ')}</div>
    </div></body></html>`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  await page.screenshot({ path: file, omitBackground: true });
  await page.close();
}

// PWA
const icons = path.join(here, 'src/icons');
await render(path.join(icons, 'icon-192.png'), 192, 192);
await render(path.join(icons, 'icon-512.png'), 512, 512);
await render(path.join(icons, 'icon-maskable-512.png'), 512, 512, { glyph: 0.44 });
await render(path.join(icons, 'apple-touch-icon.png'), 180, 180);

// Android (only when the native project exists)
const res = path.join(here, 'android/app/src/main/res');
if (fs.existsSync(res)) {
  const densities = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
  for (const [density, size] of Object.entries(densities)) {
    const dir = path.join(res, `mipmap-${density}`);
    await render(path.join(dir, 'ic_launcher.png'), size, size);
    await render(path.join(dir, 'ic_launcher_round.png'), size, size, { round: true });
    // Adaptive icon foreground: 108dp canvas, glyph inside the 66dp safe zone.
    await render(path.join(dir, 'ic_launcher_foreground.png'), size * 2.25, size * 2.25, { glyph: 0.42, transparent: true });
  }
  for (const dir of fs.readdirSync(res).filter(name => name.startsWith('drawable'))) {
    const splash = path.join(res, dir, 'splash.png');
    if (!fs.existsSync(splash)) continue;
    const { width, height } = await (async () => {
      const page = await browser.newPage();
      const data = fs.readFileSync(splash).toString('base64');
      const dims = await page.evaluate(src => new Promise(resolve => {
        const img = new Image();
        img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight });
        img.src = src;
      }), `data:image/png;base64,${data}`);
      await page.close();
      return dims;
    })();
    await render(splash, width, height, { glyph: 0.22 });
  }
  const bg = path.join(res, 'values/ic_launcher_background.xml');
  fs.writeFileSync(bg, fs.readFileSync(bg, 'utf8').replace(/#[0-9A-Fa-f]{6}/, '#101010'));
}
await browser.close();
console.log('Icons updated');
