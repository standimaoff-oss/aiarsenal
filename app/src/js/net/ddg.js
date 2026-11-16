/* ═══════════════════════════════════════════════════════════
   DuckDuckGo HTML: устойчивый парсер + транспорт.

   Почему не просто fetch(): у html.duckduckgo.com нет CORS.
   Транспорт подставляется снаружи:
   - Заход 4 (Tauri): Rust-сторонний HTTP (без CORS и прокси);
   - опция «Читать через прокси»: allorigins.win/raw? (запросы видит сторонний сервис);
   - иначе этот провайдер не используется вовсе.
   ═══════════════════════════════════════════════════════════ */
import { stripHtml } from '../netutil.js';

/** Чистый парсер — тестируется на фикстурах. */
export function parseDdgHtml(html, limit = 15) {
  const out = [];
  if (!html) return out;
  const linkRe = /<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while ((m = linkRe.exec(html)) !== null && out.length < limit) {
    let url = decodeDdgHref(m[1]);
    if (!url || !/^https?:\/\//i.test(url)) continue;
    if (/duckduckgo\.com\/(l\/)?$/.test(url) || url.includes('duckduckgo.com/y.js')) continue;
    const title = stripHtml(m[2]);
    if (!title) continue;
    // сниппет ищем в ближайших 2000 символах после ссылки
    const zone = html.slice(m.index, m.index + 2000);
    const snipMatch = zone.match(/<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)<\/a>/i);
    const snippet = snipMatch ? stripHtml(snipMatch[1]).slice(0, 240) : '';
    if (out.some((r) => r.url === url)) continue;
    let domain = '';
    try { domain = new URL(url).hostname.replace(/^www\./, ''); } catch { domain = ''; }
    out.push({ title, url, snippet, source: domain || 'web' });
  }
  return out;
}

function decodeDdgHref(href) {
  try {
    const u = new URL(href, 'https://duckduckgo.com');
    const uddg = u.searchParams.get('uddg');
    if (uddg) return decodeURIComponent(uddg);
    if (u.protocol === 'https:' || u.protocol === 'http:') return href;
    return null;
  } catch {
    // относительный href или мусор — пробуем как абсолютный
    if (/^https?:\/\//i.test(href)) return href;
    return null;
  }
}

/** Транспорт по умолчанию: Tauri-мост (Заход 4) → allorigins (если пользователь включил прокси). */
export async function ddgTransport(url) {
  if (typeof window !== 'undefined' && typeof window.__ORAKUL_TAURI_FETCH__ === 'function') {
    return window.__ORAKUL_TAURI_FETCH__(url);
  }
  const res = await fetch(`https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`, { signal: AbortSignal.timeout(12000) });
  if (!res.ok) throw new Error(`прокси allorigins: HTTP ${res.status}`);
  return res.text();
}

export async function searchDdg(query, { limit = 10, transport } = {}) {
  const html = await (transport || ddgTransport)(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`);
  const results = parseDdgHtml(html, limit);
  if (!results.length) throw new Error('DuckDuckGo не вернул разборимых результатов');
  return results;
}
