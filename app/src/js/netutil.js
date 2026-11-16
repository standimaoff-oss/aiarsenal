/* Общие сетевые утилиты: таймаут, очистка HTML. Без зависимостей. */

export async function fetchJSON(url, { timeout = 8000, signal, headers } = {}) {
  const res = await fetch(url, {
    headers: { Accept: 'application/json', ...(headers || {}) },
    signal: signal ?? AbortSignal.timeout(timeout),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** Очень аккуратная очистка HTML: вырезаем скрипты/стили и теги, схлопываем пробелы. */
export function stripHtml(html) {
  let t = String(html || '');
  t = t.replace(/<script[\s\S]*?<\/script>/gi, ' ');
  t = t.replace(/<style[\s\S]*?<\/style>/gi, ' ');
  t = t.replace(/<noscript[\s\S]*?<\/noscript>/gi, ' ');
  t = t.replace(/<!--[\s\S]*?-->/g, ' ');
  t = t.replace(/<svg[\s\S]*?<\/svg>/gi, ' ');
  // блочные элементы → перевод строки, чтобы не склеивать слова
  t = t.replace(/<\/(p|div|li|h[1-6]|tr|br)>/gi, '\n');
  t = t.replace(/<[^>]+>/g, ' ');
  t = t.replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"')
       .replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>')
       .replace(/&#(\d+);/g, (_, n) => { try { return String.fromCharCode(Number(n)); } catch { return ' '; } });
  t = t.replace(/[ \t]+/g, ' ').replace(/\n[ \t]+/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  return t;
}

/** Примерный «размер» текста в КБ (для отображения в трейсе). */
export function kbOf(text) {
  return Math.max(1, Math.round(String(text || '').length / 1024));
}
