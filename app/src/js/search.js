/* ═══════════════════════════════════════════════════════════
   Оракул — живой поиск и чтение страниц (Заход 3).

   Как устроено без ключей:
   - Wikipedia (MediaWiki API) — работает напрямую из браузера (CORS открыт);
   - DuckDuckGo (html.duckduckgo.com) — CORS закрыт, поэтому:
       · в Tauri (Заход 4) — нативный HTTP через мост window.__ORAKUL_TAURI_FETCH__;
       · в браузере — только если пользователь включил прокси (allorigins.win);
       · иначе DDG пропускается, и остаётся честная Wikipedia.
   Список источников всегда полный: кто ответил, кто нет и почему.
   ═══════════════════════════════════════════════════════════ */

import { searchWikiBoth, wikiExtract } from './net/wiki.js';
import { searchDdg, ddgTransport } from './net/ddg.js';
import { stripHtml, kbOf } from './netutil.js';

const isTauri = () => typeof window !== 'undefined' && typeof window.__ORAKUL_TAURI_FETCH__ === 'function';

async function readableFetch(url, { timeout = 12000, signal } = {}) {
  if (isTauri()) {
    return window.__ORAKUL_TAURI_FETCH__(url);
  }
  const res = await fetch(`https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`, {
    signal: signal || AbortSignal.timeout(timeout),
  });
  if (!res.ok) throw new Error(`прокси: HTTP ${res.status}`);
  return res.text();
}

const dedupe = (items) => {
  const seen = new Set();
  const out = [];
  for (const r of items) {
    const key = (r.url || '').replace(/#.*$/, '').replace(/\/$/, '').toLowerCase();
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(r);
  }
  return out;
};

/**
 * Поиск по сети. Возвращает { ok, results[], via, durationMs, errors[] }.
 * results: { title, url, snippet, source } — ≤ limit.
 */
export async function liveSearch(query, { limit = 12, signal, proxyEnabled = false } = {}) {
  const started = Date.now();
  const errors = [];
  let ddg = [];

  const canDdg = isTauri() || proxyEnabled;
  if (canDdg) {
    try {
      ddg = await searchDdg(query, { limit });
    } catch (e) {
      errors.push(`DuckDuckGo: ${e?.message || e}`);
    }
  }

  let wiki = [];
  try {
    wiki = await searchWikiBoth(query, { limit, signal });
  } catch (e) {
    if (signal?.aborted) throw e;
    errors.push(`Wikipedia: ${e?.message || e}`);
  }

  // DDG вперёд (это и есть «веб»), википедия добивает
  const results = dedupe([...ddg, ...wiki]).slice(0, limit);
  const durationMs = Date.now() - started;

  if (!results.length) {
    return { ok: false, results: [], errors: errors.length ? errors : ['нет сети'], durationMs };
  }
  return {
    ok: true,
    results,
    via: { ddg: ddg.length, wiki: wiki.length },
    durationMs,
    errors,
  };
}

/**
 * Читает верхние результаты. Википедия — выдержки через API;
 * остальные — если есть Tauri-мост или включённый прокси.
 * Помечает results[i].read / .kb. Возвращает { excerpts, readCount, kb, readUrls, skipped }.
 */
export async function readTopResults(results, { reads = 2, signal, proxyEnabled = false } = {}) {
  const excerpts = [];
  const readUrls = [];
  let kb = 0;
  let skipped = 0;
  const canReadAny = isTauri() || proxyEnabled;

  for (const r of results) {
    if (excerpts.length >= reads) break;
    try {
      if (/wikipedia\.org\/wiki\//.test(r.url || '')) {
        const lang = (r.url.match(/^https?:\/\/(\w+)\.wikipedia\.org/) || [, 'ru'])[1];
        const title = decodeURIComponent(r.url.split('/wiki/')[1] || '').replace(/_/g, ' ');
        if (!title) { skipped++; continue; }
        const ex = await wikiExtract(title, { lang, signal, maxChars: 1500 });
        if (!ex.ok) { skipped++; continue; }
        excerpts.push({ title: r.title, url: ex.url, text: ex.text });
        kb += ex.kb;
        r.read = true; r.kb = ex.kb;
        readUrls.push(ex.url);
        continue;
      }
      if (!canReadAny) { skipped++; continue; } // браузер без прокси: произвольные страницы не читаем
      const html = await readableFetch(r.url, { signal });
      const text = stripHtml(html).slice(0, 1500);
      if (text.length < 120) { skipped++; continue; }
      const k = kbOf(text);
      excerpts.push({ title: r.title, url: r.url, text });
      kb += k;
      r.read = true; r.kb = k;
      readUrls.push(r.url);
    } catch (e) {
      if (signal?.aborted) break;
      skipped++;
    }
  }
  return { excerpts, readCount: excerpts.length, kb: Math.round(kb), readUrls, skipped };
}
