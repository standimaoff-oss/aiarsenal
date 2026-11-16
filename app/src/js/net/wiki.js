/* ═══════════════════════════════════════════════════════════
   Wikipedia (MediaWiki API, CORS открыт): поиск статей и выдержки.
   Это «живая» база: настоящие заголовки, URL и текст.
   ═══════════════════════════════════════════════════════════ */
import { fetchJSON, stripHtml, kbOf } from '../netutil.js';

const API = (lang) => `https://${lang}.wikipedia.org/w/api.php`;

/** Поиск статей. Возвращает массив {title, url, snippet, source}. */
export async function searchWiki(query, { limit = 12, signal, lang = 'ru' } = {}) {
  const params = new URLSearchParams({
    action: 'query',
    list: 'search',
    srlimit: String(Math.min(Math.max(limit, 1), 20)),
    srprop: 'snippet',
    srsearch: query,
    format: 'json',
    origin: '*',
  });
  const data = await fetchJSON(`${API(lang)}?${params}`, { timeout: 9000, signal });
  const items = data?.query?.search || [];
  return items.map((it) => ({
    title: stripHtml(it.title),
    url: `https://${lang}.wikipedia.org/wiki/${encodeURIComponent(it.title).replace(/%20/g, '_')}`,
    snippet: stripHtml(it.snippet).slice(0, 240),
    source: `${lang}.wikipedia.org`,
    lang,
  }));
}

/** Поиск на обоих языках: сначала русская, потом английская добивка. */
export async function searchWikiBoth(query, { limit = 12, signal } = {}) {
  const out = [];
  try {
    out.push(...await searchWiki(query, { limit: Math.min(limit, 10), signal, lang: 'ru' }));
  } catch (e) {
    if (signal?.aborted) throw e;
    /* русская не ответила — пробуем английскую */
  }
  if (out.length < limit) {
    try {
      out.push(...await searchWiki(query, { limit: limit - out.length, signal, lang: 'en' }));
    } catch (e) {
      if (signal?.aborted) throw e;
      if (!out.length) throw e; // обе упали — пусть выше решит
    }
  }
  return out.slice(0, limit);
}

/** Полный текст статьи (чистый) по заголовку. */
export async function wikiExtract(title, { lang = 'ru', signal, maxChars = 1800 } = {}) {
  const params = new URLSearchParams({
    action: 'query',
    prop: 'extracts',
    explaintext: '1',
    redirects: '1',
    exsectionformat: 'plain',
    titles: title,
    format: 'json',
    origin: '*',
  });
  const data = await fetchJSON(`${API(lang)}?${params}`, { timeout: 9000, signal });
  const pages = data?.query?.pages || {};
  const page = Object.values(pages)[0];
  const text = (page?.extract || '').trim();
  if (!text) return { ok: false, error: 'статья не найдена' };
  const clipped = text.length > maxChars ? text.slice(0, maxChars) + '…' : text;
  return {
    ok: true,
    text: clipped,
    url: `https://${lang}.wikipedia.org/wiki/${encodeURIComponent(page.title || title).replace(/%20/g, '_')}`,
    kb: kbOf(clipped),
  };
}
