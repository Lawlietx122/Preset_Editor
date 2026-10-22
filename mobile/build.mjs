// Builds the mobile/PWA bundle in www/ from the desktop index.html.
// Usage: node build.mjs [path/to/desktop/index.html]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const source = process.argv[2] || path.join(here, 'source', 'index.desktop.html');
const out = path.join(here, 'www');
const pkg = JSON.parse(fs.readFileSync(path.join(here, 'package.json'), 'utf8'));

let html = fs.readFileSync(source, 'utf8');

function replaceOnce(pattern, replacement, label) {
  if (!pattern.test(html)) throw new Error(`Patch point not found: ${label}`);
  html = html.replace(pattern, replacement);
}

replaceOnce(
  /<meta name="viewport"[^>]*>/,
  [
    '<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover, interactive-widget=resizes-content">',
    '  <meta name="theme-color" content="#101010">',
    '  <meta name="mobile-web-app-capable" content="yes">',
    '  <meta name="apple-mobile-web-app-capable" content="yes">',
    '  <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">',
    '  <meta name="apple-mobile-web-app-title" content="Preset Editor">',
    '  <link rel="manifest" href="manifest.webmanifest">',
    '  <link rel="icon" href="icons/icon-192.png">',
    '  <link rel="apple-touch-icon" href="icons/apple-touch-icon.png">',
    '  <script src="workspace-shim.js"></script>'
  ].join('\n'),
  'viewport meta'
);
replaceOnce(/<\/head>/, '  <link rel="stylesheet" href="mobile.css">\n</head>', '</head>');
replaceOnce(/<\/body>(?![\s\S]*<\/body>)/, `  <script>window.PRESET_EDITOR_MOBILE_VERSION = ${JSON.stringify(pkg.version)};</script>\n  <script src="mobile.js"></script>\n</body>`, '</body>');

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'index.html'), html);
for (const name of ['workspace-shim.js', 'mobile.css', 'mobile.js', 'manifest.webmanifest']) {
  fs.copyFileSync(path.join(here, 'src', name), path.join(out, name));
}
fs.cpSync(path.join(here, 'src', 'icons'), path.join(out, 'icons'), { recursive: true });
const sw = fs.readFileSync(path.join(here, 'src', 'sw.js'), 'utf8')
  .replace('__CACHE_VERSION__', `${pkg.version}-${Date.now()}`);
fs.writeFileSync(path.join(out, 'sw.js'), sw);
console.log(`Built ${path.relative(here, out)}/ from ${source}`);
