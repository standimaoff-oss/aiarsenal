// Копирует wasm-ядро wllama в public/ — единое место для веб-превью и Tauri-сборки.
// Файл в git не хранится: после npm ci он всегда на месте.
import { copyFileSync, mkdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'node_modules', '@wllama', 'wllama', 'esm', 'wasm', 'wllama.wasm');
const dstDir = join(root, 'public', 'wasm');
const dst = join(dstDir, 'wllama.wasm');

if (!existsSync(src)) {
  console.warn('[orakul] wllama.wasm не найден после npm install — пропуск (локальная модель будет недоступна до следующего npm ci)');
  process.exit(0);
}
mkdirSync(dstDir, { recursive: true });
copyFileSync(src, dst);
console.log('[orakul] wasm-ядро скопировано в public/wasm/wllama.wasm');
