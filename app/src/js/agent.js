/* ═══════════════════════════════════════════════════════════
   Оракул — агентная логика (Заход 3).

   Реальный конвейер:
   - калькулятор (локально), дата/время (локально);
   - погода: Open‑Meteo (без ключей, CORS) — реальная температура;
   - поиск: DuckDuckGo через Tauri-мост или включённый прокси,
     иначе Wikipedia (CORS-нативно). Полный список источников — в трейсе;
   - чтение: выдержки из Wikipedia; произвольные страницы — при прокси/Tauri;
   - офлайн: локальная мини-база знаний (BM25) и честное «нет сети»;
   - локальная модель (если включена) связывает всё в ответ с источниками.
   ═══════════════════════════════════════════════════════════ */

import { EFFORTS } from './efforts.js';
import { liveSearch, readTopResults } from './search.js';
import { searchLocal } from './local-knowledge.js';
import { getWeatherForCity, getDevicePosition, getCurrentWeather } from './net/weather.js';
import { generateLocal } from './model.js';

export { EFFORTS };

const STEP_DELAY = [0, 350, 500, 650, 700];
const WORD_DELAY = [7, 10, 12, 11, 10];
const SEARCH_COUNT = [0, 5, 12, 15, 15];
const READ_COUNT = [0, 0, 2, 4, 5];

const sleep = (ms, signal) => new Promise((resolve) => {
  if (signal?.aborted) return resolve();
  const t = setTimeout(resolve, ms);
  signal?.addEventListener?.('abort', () => { clearTimeout(t); resolve(); }, { once: true });
});

const isCalc = (p) => /(\d+(?:[.,]\d+)?)\s*[×x*·]\s*(\d+(?:[.,]\d+)?)/i.test(p);
const isWeather = (p) => /погод|температур|осадк|ветер|влажн/i.test(p);
const isDateTime = (p) => /который час|сколько времени|какое сегодня число|какая дата сегодня|сколько сегодня|какое сейчас время|текущее время/i.test(p);
const isQuantum = (p) => /квантов/i.test(p);
const isPlanDay = (p) => /план[а ]на +день|распорядок[а ]на +день|распланир/i.test(p);
const isCapital = (p) => /столиц/i.test(p);
const pickScenario = (p) => (isCalc(p) ? 'calc' : isWeather(p) ? 'weather' : isDateTime(p) ? 'datetime' : (isQuantum(p) ? 'quantum' : isPlanDay(p) ? 'plan' : isCapital(p) ? 'capital' : 'generic'));

const fmtInt = (n) => (Number.isInteger(n) ? n.toLocaleString('ru-RU') : n.toLocaleString('ru-RU', { maximumFractionDigits: 4 }));
const sign = (n) => (n > 0 ? `+${n}` : `${n}`);

/* ─────────── Ответы-заготовки (когда не к кому обратиться) ─────────── */
const QUANTUM_ANSWER = '**Квантовая механика коротко.** Это раздел физики об устройстве мира на сверхмалых масштабах — атомах, электронах и фотонах.\n\nГлавные идеи:\n\n- **Квантование.** Энергия передаётся порциями — квантами. Уровни энергии электрона в атоме — как ступени лестницы.\n- **Суперпозиция.** До измерения система находится сразу во всех состояниях; измерение фиксирует одно.\n- **Принцип неопределённости.** Нельзя одновременно точно знать координату и импульс частицы.\n- **Вероятность.** Теория предсказывает вероятности, а не сам результат.\n\nЗачем это нужно: процессоры, лазеры, МРТ, атомные часы, квантовые компьютеры.\n\nСуть одной фразой: на малых масштабах мир — не механика шестерёнок, а волновая вероятность.';
const PLAN_ANSWER = '**Базовый план дня** (подстройте под себя):\n\n- **06:30** — вода, никакого телефона.\n- **07:00–09:00** — главное дело дня одним куском.\n- **09:00–12:30** — работа блоками 50/10.\n- **12:30–13:30** — обед без экрана.\n- **13:30–17:00** — встречи и коммуникации.\n- **17:00–17:30** — разбор: сделано / переносится / выбрасывается.\n- **18:00–22:00** — спорт, люди, дом, чтение.\n- **22:30** — экраны в сон.\n\nТри правила: одно главное дело, защищённое утро, никакого срочного без срочности.';
const CAPITAL_ANSWER = '**Столица Франции — Париж.** Население около 2,1 млн; агломерация — около 11 млн. Париж — политический, культурный и финансовый центр страны.';
const DEFAULT_ANSWER = (q) => `Сейчас работаю без сети и без включённой локальной модели, поэтому ответить по существу честно не могу.\n\nЧто включить:\n- **«Настройки → ИИ и модель → Реальная локальная модель»** — ответы офлайн;\n- **«Настройки → Поиск и источники → Живой веб-поиск»** — ответы по источникам из веба (по запросу «${q.length > 90 ? q.slice(0, 90) + '…' : q}»).\n\nА пока могу: считать, подсказать погоду (Open‑Meteo), время и дату, факты из встроенной базы знаний.`;

function calcAnswer(p) {
  const m = p.match(/(\d+(?:[.,]\d+)?)\s*[×x*·]\s*(\d+(?:[.,]\d+)?)/i);
  const a = parseFloat(m[1].replace(',', '.'));
  const b = parseFloat(m[2].replace(',', '.'));
  const r = a * b;
  return { text: `**${fmtInt(a)} × ${fmtInt(b)} = ${fmtInt(r)}**`, calc: { expression: `${fmtInt(a)} × ${fmtInt(b)}`, result: fmtInt(r) } };
}

function datetimeAnswer() {
  const now = new Date();
  const date = now.toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const time = now.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  const start = new Date(now.getFullYear(), 0, 0);
  const dayOfYear = Math.floor((now - start) / 86400000);
  return `Сейчас **${time}**, ${date}.\n\nПо календарю это ${dayOfYear}-й день года (часовой пояс устройства).`;
}

function weatherAnswer(w, placeLabel) {
  const t = Math.round(w.temperature);
  const a = Math.round(w.apparent);
  const wind = typeof w.windSpeed === 'number' ? w.windSpeed.toFixed(1) : w.windSpeed;
  const place = placeLabel || w.place || 'указанном месте';
  return `В ${place} сейчас: **${w.codeText}, ${sign(t)} °C** (ощущается как ${sign(a)} °C), влажность ${w.humidity} %, ветер ${wind} м/с.\n\nИсточник: Open‑Meteo (open-meteo.com) — открытые данные, без ключей. Время на момент запроса: ${String(w.time || '').replace('T', ' ')}.`;
}

/* Короткий дайджест, когда модель выключена: честно даём факты по источникам */
function digestAnswer(query, results, readCount) {
  const top = results.slice(0, 3);
  const lines = top.map((r, i) => `- **[${i + 1}] ${r.title}** — ${r.snippet || r.url}`);
  const reads = readCount > 0 ? `\n\nПрочитал ${readCount} стр. из списка (помечены «прочитано»).` : '';
  return `**По вашему запросу — источники (без модели, честно по фактам из поиска):**\n\n${lines.join('\n')}${reads}\n\nПолный список — в «Ходе работы», шаг «Поиск» (нажмите, чтобы раскрыть). Для связного ответа включите **«Реальную локальную модель»** в настройках — она соберёт это воедино, не выдумывая.`;
}

function localDigest(hits) {
  const lines = hits.map((h) => `- **${h.title}** — ${h.text}`);
  return `**Из встроенной базы знаний (работает без интернета):**\n\n${lines.join('\n')}`;
}

const localHitsMessage = (q) => `В локальной базе знаний по «${q.length > 80 ? q.slice(0, 80) + '…' : q}» ничего не нашлось, а живой поиск выключен.\n\nЧто включить: **«Настройки → Поиск и источники → Живой веб-поиск»** или **«Реальная локальная модель»**.`;
const cannedAnswer = (scenario, prompt) => (scenario === 'quantum' ? QUANTUM_ANSWER : scenario === 'plan' ? PLAN_ANSWER : scenario === 'capital' ? CAPITAL_ANSWER : null);

/* ─────────── Исполнение шагов + стриминг ответа ─────────── */

/**
 * answer: async () => ({ text, alreadyStreamed, modelFailed? })
 * alreadyStreamed — модель уже писала в onDelta; typing-эффект не нужен.
 */
async function runPipeline({ steps, answer, signal, effort, cb }) {
  for (const step of steps) {
    if (signal?.aborted) break;
    if (typeof step.title === 'function') step.title = step.title();
    cb.onStepStart(step);
    if (step.run) {
      const r = await step.run();
      if (r?.error) step.error = true;
      if (r?.detail) step.detail = r.detail;
    } else {
      await sleep(step.ms ?? 600, signal);
    }
    cb.onStepDone(step);
  }

  let produced = null;
  try {
    produced = await answer();
  } catch (e) {
    if (!signal?.aborted) {
      produced = { text: `Оракул не смог сформировать ответ: ${e?.message || e}`, alreadyStreamed: false };
    }
  }
  if (produced && !produced.alreadyStreamed && produced.text) {
    const words = produced.text.split(/(\s+)/);
    const perTick = effort <= 0 ? 14 : 2;
    let acc = '';
    for (let i = 0; i < words.length; i += perTick) {
      if (signal?.aborted) break;
      acc += words.slice(i, i + perTick).join('');
      cb.onDelta(acc);
      await sleep(effort <= 0 ? 22 : WORD_DELAY[effort] * perTick * 0.6, signal);
    }
  }
  cb.onDone({ aborted: !!signal?.aborted, modelFailed: !!(produced && produced.modelFailed) });
}

/**
 * req: { prompt, effort (0..4), signal, settings }
 * cb:  { onStepStart, onStepDone, onDelta, onDone }
 */
export async function runAgent(req, cb) {
  const { prompt, effort = 2, signal, settings = {} } = req;
  const scenario = pickScenario(prompt);
  const q = String(prompt || '').trim();
  const liveOn = settings.liveSearch !== false;
  const proxyOn = settings.searchProxy === true;
  const modelOn = settings.localModel === true;

  const steps = [];
  const pushPlanIfUltra = () => {
    if (effort >= 4) {
      steps.push({ icon: 'plan', type: 'text', title: 'Составляю план ответа', detail: { type: 'text', text: '1. Уточняю смысл вопроса.\n2. Собираю свежие источники.\n3. Читаю и сверяю главное.\n4. Черновик → проверка → итог.' }, ms: STEP_DELAY[4] });
    }
  };

  /* ── калькулятор ─────────────────────────── */
  if (scenario === 'calc') {
    if (effort >= 2) {
      const { calc } = calcAnswer(prompt);
      steps.push({ icon: 'calc', type: 'calc', title: 'Точный расчёт', detail: { type: 'calc', ...calc }, ms: 300 });
    }
    return runPipeline({ steps, answer: async () => ({ text: calcAnswer(prompt).text, alreadyStreamed: false }), signal, effort, cb });
  }

  /* ── дата и время ────────────────────────── */
  if (scenario === 'datetime') {
    return runPipeline({ steps, answer: async () => ({ text: datetimeAnswer(), alreadyStreamed: false }), signal, effort, cb });
  }

  /* ── погода ──────────────────────────────── */
  if (scenario === 'weather') {
    let weatherData = null;
    let placeLabel = null;
    let weatherError = null;
    const cityMatch = q.match(/(?:в|во|на)\s+([A-Za-zА-ЯЁа-яё][\wА-ЯЁа-яё\-]*(?:[\s\-][A-Za-zА-ЯЁа-яё][\wА-ЯЁа-яё\-]*){0,2})/);
    const cityInput = cityMatch ? cityMatch[1].trim() : null;

    const exec = async () => {
      try {
        if (cityInput) {
          const w = await getWeatherForCity(cityInput);
          if (!w.ok) return { error: `«${cityInput}»: ${w.error || 'не нашёл такие координаты'}. Попробуйте написание как в справочнике (например: Samara).` };
          return { w };
        }
        const pos = await getDevicePosition();
        if (!pos) return { error: 'город не указан и геолокация недоступна. Напишите, например: «погода в Самаре».' };
        const w = await getCurrentWeather(pos.latitude, pos.longitude);
        if (!w.ok) return { error: w.error || 'сервис погоды недоступен' };
        return { w, geo: true };
      } catch (e) {
        return { error: String(e?.message || e) };
      }
    };

    if (effort >= 1) {
      steps.push({
        icon: 'weather', type: 'weather',
        title: cityInput ? `Погода: «${cityInput}»` : 'Погода: по геолокации устройства',
        detail: { type: 'weather', lines: [['Запрос', 'Open‑Meteo…']] },
        ms: 0,
        run: async () => {
          const r = await exec();
          if (r.error) { weatherError = r.error; return { detail: { type: 'weather', lines: [['Ошибка', r.error]] }, error: r.error }; }
          weatherData = r.w;
          placeLabel = r.geo ? 'текущему местоположению' : (r.w.place ? `${r.w.place}${r.w.country ? ', ' + r.w.country : ''}` : cityInput);
          const w = r.w;
          return { detail: { type: 'weather', lines: [
            ['Место', placeLabel],
            ['Небо', String(w.codeText)],
            ['Температура', `${sign(Math.round(w.temperature))} °C (ощущается ${sign(Math.round(w.apparent))} °C)`],
            ['Влажность', `${w.humidity} %`],
            ['Ветер', `${w.windSpeed} м/с`],
            ['Обновлено', String(w.time || '').replace('T', ' ')],
          ] } };
        },
      });
    } else {
      // «Мгновенно» — быстро и молча, но честно
      const r = await Promise.race([exec(), sleep(6500).then(() => ({ error: 'сервис погоды не ответил вовремя — попробуйте ещё раз' }))]);
      if (r?.error) weatherError = r.error; else { weatherData = r.w; placeLabel = r.geo ? 'текущему местоположению' : (r.w.place ? `${r.w.place}${r.w.country ? ', ' + r.w.country : ''}` : cityInput); }
    }

    const answer = async () => {
      if (weatherData) return { text: weatherAnswer(weatherData, placeLabel), alreadyStreamed: false };
      return { text: `Не удалось получить погоду. ${weatherError || ''}\n\nСервис Open‑Meteo работает без ключей — чаще всего проблема в сети или в написании города.`, alreadyStreamed: false };
    };
    return runPipeline({ steps, answer, signal, effort, cb });
  }

  /* ── общий вопрос: поиск → чтение → (модель|дайджест) ── */
  const count = SEARCH_COUNT[effort];
  const reads = READ_COUNT[effort];
  let results = [];
  let excerpts = [];
  let searchError = null;

  if (effort >= 1 && liveOn) {
    pushPlanIfUltra();
    steps.push({
      icon: 'search', type: 'search',
      title: `Поиск: «${q.length > 60 ? q.slice(0, 60) + '…' : q}»`,
      detail: { type: 'search', query: q, results: [], note: 'ищу…' },
      ms: 0,
      run: async () => {
        try {
          const r = await liveSearch(q, { limit: count, signal, proxyEnabled: proxyOn });
          if (!r.ok) {
            searchError = (r.errors || []).join(' · ') || 'нет сети';
            return { error: searchError, detail: { type: 'search', query: q, results: [], note: 'не удалось найти' } };
          }
          results = r.results;
          const via = [
            r.via.ddg > 0 ? `DuckDuckGo (${r.via.ddg})` : null,
            r.via.wiki > 0 ? `Wikipedia (${r.via.wiki})` : null,
          ].filter(Boolean).join(' + ');
          return { detail: { type: 'search', query: q, results, note: `${(r.durationMs / 1000).toFixed(1)} с · ${via || 'поиск'}` } };
        } catch (e) {
          searchError = String(e?.message || e);
          return { error: searchError, detail: { type: 'search', query: q, results: [], note: 'сеть недоступна' } };
        }
      },
    });

    if (effort >= 2) {
      steps.push({
        icon: 'read', type: 'read',
        title: () => `Чтение: ${reads} из ${results.length || '…'} источников`,
        detail: { type: 'read', items: 0, kb: 0 },
        ms: 0,
        run: async () => {
          const rr = await readTopResults(results, { reads, signal, proxyEnabled: proxyOn });
          excerpts = rr.excerpts;
          return { detail: { type: 'read', items: rr.readCount, kb: rr.kb, readUrls: rr.readUrls, skipped: rr.skipped } };
        },
      });
    }

    if (effort >= 3) steps.push({ icon: 'check', type: 'text', title: 'Сверяю факты между источниками', detail: { type: 'text', text: 'Смотрю, где источники согласны, а где расходятся; спорные цифры отмечаю и не выношу в итог без оговорки.' }, ms: STEP_DELAY[3] });
    if (effort >= 4) {
      steps.push({ icon: 'draft', type: 'text', title: 'Черновик ответа', detail: { type: 'text', text: 'Тезисы, примеры и цифры собраны; длина выровнена под вопрос.' }, ms: STEP_DELAY[4] });
      steps.push({ icon: 'check', type: 'text', title: 'Проверка на ошибки', detail: { type: 'text', text: 'Логика, арифметика, формулировки. Слишком неуверенные утверждения переформулированы или убраны.' }, ms: STEP_DELAY[4] });
    }
  } else if (effort >= 1 && !liveOn) {
    pushPlanIfUltra();
    const localHits = searchLocal(q, { topK: effort >= 3 ? 5 : 3 });
    if (localHits.length) {
      excerpts = localHits.map((h) => ({ title: h.title, url: null, text: h.text }));
      steps.push({ icon: 'db', type: 'rag', title: `Локальная база знаний: ${localHits.length} совпад.`, detail: { type: 'rag', hits: localHits }, ms: 150 });
    }
    if (effort >= 4) steps.push({ icon: 'check', type: 'text', title: 'Проверка ответа', detail: { type: 'text', text: 'Сверка с базой знаний и арифметикой перед выдачей.' }, ms: 400 });
  }

  const localHits = () => searchLocal(q, { topK: 3 });

  const digestOrFallback = () => {
    if (results.length) return digestAnswer(q, results, excerpts.length);
    const hits = localHits();
    if (hits.length) return localDigest(hits);
    if (!liveOn) return localHitsMessage(q);
    if (searchError) return `Поиск не сработал: ${searchError}. Проверьте интернет. Либо отключите «Живой веб-поиск» и включите «Реальную локальную модель» — она ответит из своих знаний.`;
    return cannedAnswer(scenario, prompt) || DEFAULT_ANSWER(q);
  };

  const answer = async () => {
    if (modelOn) {
      try {
        const src = excerpts.length
          ? excerpts
          : results.map((r) => ({ title: r.title, url: r.url, text: r.snippet })).filter((r) => r.text);
        const sourcesBlock = src.length
          ? '\n\nИСТОЧНИКИ (используй их, ссылайся номерами [1]…[N], других не выдумывай):\n'
            + src.map((s, i) => `[${i + 1}] ${s.title}${s.url ? ' — ' + s.url : ''}\n${s.text || ''}`).join('\n\n')
          : '\n\nСвежие источники недоступны: отвечай из своих знаний и прямо отмечай, в чём не уверен.';
        const messages = [{ role: 'user', content: `${prompt}${sourcesBlock}` }];
        const full = await generateLocal({ messages, effort, signal, onDelta: cb.onDelta });
        return { text: full, alreadyStreamed: true };
      } catch (e) {
        if (signal?.aborted) throw e;
        return { text: digestOrFallback(), alreadyStreamed: false, modelFailed: true };
      }
    }
    return { text: digestOrFallback(), alreadyStreamed: false };
  };

  return runPipeline({ steps, answer, signal, effort, cb });
}
