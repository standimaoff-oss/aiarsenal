/* ═══════════════════════════════════════════════════════════
   Оракул — агентная логика (Заход 1: демо-движок).
   Показывает UX хода работы: поиск → чтение → ответ.
   Заход 2 подключит настоящее локальное ядро (GGUF, офлайн),
   Заход 3 — живой веб-поиск без ключей.
   ═══════════════════════════════════════════════════════════ */

export const EFFORTS = [
  { id: 'instant',  name: 'Мгновенно', desc: 'Только суть. Без поиска и шагов — ответ сразу.',        hint: '≈1 с',  dots: 1 },
  { id: 'fast',     name: 'Быстро',    desc: 'Сжатый ответ, один быстрый поиск.',                      hint: '≈2 с',  dots: 2 },
  { id: 'standard', name: 'Стандарт',  desc: 'Поиск по вебу, краткий ответ с источниками.',            hint: '≈4 с',  dots: 3 },
  { id: 'deep',     name: 'Глубоко',   desc: 'Поиск, чтение страниц, сверка фактов.',                  hint: '≈7 с',  dots: 4 },
  { id: 'ultra',    name: 'Ультра',    desc: 'План → поиск → черновик → самопроверка → ответ.',        hint: '≈10 с', dots: 5 },
];

const STEP_DELAY = [0, 420, 700, 950, 1150];   // мс на шаг по уровню
const WORD_DELAY = [7, 11, 13, 12, 11];        // мс на «слово» при стриминге

const sleep = (ms, signal) => new Promise((resolve) => {
  if (signal?.aborted) return resolve();
  const t = setTimeout(resolve, ms);
  signal?.addEventListener?.('abort', () => { clearTimeout(t); resolve(); }, { once: true });
});

/* Детерминированный ГПСЧ — «размеры файлов» и тайлы стабильны между показами */
function hashStr(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}
function mulberry32(seed) {
  return function () {
    seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const DOMAINS = ['ru.wikipedia.org', 'habr.com', 'dzen.ru', 'nplus1.ru', 'cyberleninka.ru', 'ria.ru', 'tass.ru', 'lenta.ru', 'yandex.ru', 'arxiv.org'];
const TITLE_TEMPLATES = [
  '{q} — Википедия',
  '{q}: главное за пять минут',
  '«{q}» — подробно и с примерами',
  '{q}. Что важно знать новичку',
  'Как устроено: {q}',
  '{q} — разбор на Habr',
  '{q}: последние события',
  '{q}. История, разбор, источники',
  '{q} простыми словами',
  '{q} — вопросы и ответы',
  '{q}. Официальные материалы',
  '{q} — что показывает опыт',
  '{q}. Исследование и данные',
  'Выжимка по теме: {q}',
  'Глубокий разбор: {q}',
];

function makeSearchResults(query, count, reads) {
  const rnd = mulberry32(hashStr(query.trim().toLowerCase() || 'оракул'));
  const q = query.trim().replace(/[?!.…]+$/u, '');
  const results = [];
  for (let i = 0; i < count; i++) {
    const domain = DOMAINS[(i * 7 + Math.floor(rnd() * 3)) % DOMAINS.length];
    const title = TITLE_TEMPLATES[i % TITLE_TEMPLATES.length].replace('{q}', q);
    const kb = Math.round(6 + rnd() * 58); // объём «загруженной» страницы, КБ
    results.push({
      title,
      url: `https://${domain}/wiki/${encodeURIComponent(q)}`,
      read: i < reads,
      kb,
    });
  }
  return results;
}

const isCalc = (p) => /(\d+(?:[.,]\d+)?)\s*[×x*·]\s*(\d+(?:[.,]\d+)?)/i.test(p);
const isWeather = (p) => /погод|температур|осадк|ветер|влажн/i.test(p);
const isQuantum = (p) => /квантов/i.test(p);
const isPlanDay = (p) => /план[а ]на +день|распорядок[а ]на +день|распланир/i.test(p);
const isCapital = (p) => /столиц/i.test(p);

/* ─────────── Ответы (демо-тексты, русские) ─────────── */

function calcAnswer(p) {
  const m = p.match(/(\d+(?:[.,]\d+)?)\s*[×x*·]\s*(\d+(?:[.,]\d+)?)/i);
  const a = parseFloat(m[1].replace(',', '.'));
  const b = parseFloat(m[2].replace(',', '.'));
  const r = a * b;
  const fmt = (n) => Number.isInteger(n) ? n.toLocaleString('ru-RU') : n.toLocaleString('ru-RU', { maximumFractionDigits: 4 });
  return { text: `**${fmt(a)} × ${fmt(b)} = ${fmt(r)}**\n\nЕсли нужны остальные действия — просто напишите пример: деление, проценты, степени, корни.`, calc: { expression: `${fmt(a)} × ${fmt(b)}`, result: fmt(r) } };
}

const WEATHER_ANSWER = 'В Самаре сейчас: **переменная облачность, +12 °C**, ветер ЮЗ, 3 м/с, влажность 46 %, осадков не ожидается; к вечеру +9 °C.\n\nОбратите внимание: это демонстрационные данные. Живая погода появится вместе с веб-поиском в Заходе 3 — к тому моменту ответ будет строиться по открытым источникам в реальном времени.';

const QUANTUM_ANSWER = '**Квантовая механика коротко.** Это раздел физики об устройстве мира на сверхмалых масштабах — атомах, электронах и фотонах.\n\nГлавные идеи:\n\n- **Квантование.** Энергия передаётся порциями — квантами. Уровни энергии электрона в атоме — как ступени лестницы, «между ступенями» энергии не бывает.\n- **Суперпозиция.** До измерения система находится сразу во всех возможных состояниях; измерение фиксирует одно. Классический образ — подброшенная монета в полёте: она ещё не орёл и не решка.\n- **Принцип неопределённости.** Нельзя одновременно точно знать координату и импульс частицы: точнее одно — размытее другое.\n- **Вероятность.** Теория предсказывает вероятности результатов (через волновую функцию), а не сам результат.\n\nЗачем это нужно: полупроводники и процессоры, лазеры, МРТ, атомные часы, светодиоды, квантовые компьютеры.\n\nСуть одной фразой: на малых масштабах мир — не механика шестерёнок, а волновая вероятность, и интуиция здесь обманывает чаще, чем где-либо.';

const PLAN_ANSWER = '**Базовый план дня** (подстройте под себя):\n\n- **06:30** — вода, никакого телефона.\n- **07:00–09:00** — главное дело дня одним куском, без мессенджеров.\n- **09:00–12:30** — работа блоками 50/10: пятьдесят минут дело, десять — глаза и вода.\n- **12:30–13:30** — обед без экрана.\n- **13:30–17:00** — встречи, письма, коммуникации; мелочи пакетом вечером.\n- **17:00–17:30** — разбор: сделано / переносится / выбрасывается.\n- **18:00–22:00** — спорт, люди, дом, чтение.\n- **22:30** — всё, экраны в сон.\n\nТри правила, которые решают больше любых приложений: одно главное дело на день, защищённые утренние блоки, ничего срочного без срочности по существу.';

const CAPITAL_ANSWER = '**Столица Франции — Париж.** Население черты города ≈ 2,1 млн, агломерации — ≈ 11 млн. Париж — политический, культурный и финансовый центр страны, там же резиденция президента и Национальная ассамблея. Если нужен не факт, а планирование поездки — расскажу, что посмотреть за день.';

function defaultAnswer(prompt) {
  const q = String(prompt || '').trim().replace(/\s+/g, ' ').replace(/^./, (c) => c.toUpperCase());
  const shown = q.length > 120 ? q.slice(0, 120) + '…' : q;
  return `Это демо-версия Захода 1: настоящее локальное ядро (~0,5 млрд параметров, офлайн) подключится в Заходе 2, а живой веб-поиск — в Заходе 3.\n\nЧто я уже умею: мгновенно показать **ход работы** по вашему запросу «${shown}» — поиск, список сайтов, чтение страниц (нажмите на шаг, чтобы раскрыть), а также точные расчёты, погоду (демо), объяснение квантовой механики и план дня.\n\nПосле Захода 2 ответ на ваш вопрос будет давать настоящая модель на этом устройстве — без интернета и без отправки ваших данных куда-либо.`;
}

/* ─────────── Сборка шагов по уровню ─────────── */

function buildSteps(scenario, effort, prompt) {
  const q = String(prompt || '').trim();
  const steps = [];
  if (scenario === 'calc') {
    if (effort >= 2) {
      steps.push({ icon: 'calc', type: 'calc', title: 'Точный расчёт', detail: { type: 'calc', expression: '673 × 584', result: '392 432' }, ms: 420 });
    }
    return steps;
  }
  if (effort <= 0) return steps; // «Мгновенно» — без шагов

  const readsByEffort = [0, 0, 2, 4, 5];
  const countByEffort = [0, 5, 12, 15, 15];
  const searchCount = countByEffort[effort];
  const reads = readsByEffort[effort];

  if (effort >= 4) {
    steps.push({ icon: 'plan', type: 'text', title: 'Составляю план ответа', detail: { type: 'text', text: '1. Уточняю смысл вопроса.\n2. Собираю свежие источники.\n3. Читаю и сверяю главное.\n4. Черновик → проверка → итог.' }, ms: STEP_DELAY[effort] });
  }

  steps.push({
    icon: 'search', type: 'search',
    title: `Поиск: «${q.length > 60 ? q.slice(0, 60) + '…' : q}»`,
    detail: { type: 'search', query: q, results: makeSearchResults(q, searchCount, reads), note: 'Демонстрационные результаты — живой поиск подключится в Заходе 3' },
    ms: effort >= 4 ? 1600 : STEP_DELAY[effort] + 250,
  });

  if (reads > 0) {
    steps.push({
      icon: 'read', type: 'read',
      title: `Чтение: ${reads} ${reads < 5 ? 'страниц' : 'страниц'} из списка`,
      detail: { type: 'read', kb: Math.round(reads * 31), items: reads },
      ms: STEP_DELAY[effort] + 300,
    });
  }

  if (effort >= 3) {
    steps.push({ icon: 'check', type: 'text', title: 'Сверяю факты между источниками', detail: { type: 'text', text: 'Согласие источников по ключевым пунктам — высокое. Расхождения (даты, цифры) отмечены и усреднены; спорные места не попадают в ответ без оговорки.' }, ms: STEP_DELAY[effort] });
  }
  if (effort >= 4) {
    steps.push({ icon: 'draft', type: 'text', title: 'Черновик ответа', detail: { type: 'text', text: 'Собран сжатый черновик: тезисы, примеры, цифры. Длина выровнена под вопрос.' }, ms: STEP_DELAY[effort] });
    steps.push({ icon: 'check', type: 'text', title: 'Проверка на ошибки', detail: { type: 'text', text: 'Финальная сверка: логика, арифметика, опечатки, лишние слова. Слишком неуверенные утверждения переформулированы или убраны.' }, ms: STEP_DELAY[effort] });
  }
  return steps;
}

function pickScenario(prompt) {
  const p = String(prompt || '');
  if (isCalc(p)) return 'calc';
  if (isWeather(p)) return 'weather';
  if (isQuantum(p)) return 'quantum';
  if (isPlanDay(p)) return 'plan';
  if (isCapital(p)) return 'capital';
  return 'default';
}

function answerFor(scenario, prompt) {
  switch (scenario) {
    case 'calc': return calcAnswer(prompt).text;
    case 'weather': return WEATHER_ANSWER;
    case 'quantum': return QUANTUM_ANSWER;
    case 'plan': return PLAN_ANSWER;
    case 'capital': return CAPITAL_ANSWER;
    default: return defaultAnswer(prompt);
  }
}

/**
 * Запускает агента.
 * req: { prompt, effort (0..4), signal }
 * cb:  { onStepStart(step), onStepDone(step), onDelta(fullText), onDone({ aborted }) }
 */
export async function runAgent(req, cb) {
  const { prompt, effort = 2, signal } = req;
  const scenario = pickScenario(prompt);

  // Для калькулятора подставим настоящий пример вместо шаблона
  let steps = buildSteps(scenario, effort, prompt);
  if (scenario === 'calc' && steps.length) {
    const { calc } = calcAnswer(prompt);
    steps[0] = { ...steps[0], detail: { type: 'calc', expression: calc.expression, result: calc.result } };
  }

  for (const step of steps) {
    if (signal?.aborted) break;
    cb.onStepStart(step);
    await sleep(step.ms ?? 800, signal);
    cb.onStepDone(step);
  }

  const answer = answerFor(scenario, prompt);
  const words = answer.split(/(\s+)/);
  const perTick = effort <= 0 ? 14 : 1;      // «Мгновенно» — крупными кусками
  let acc = '';
  for (let i = 0; i < words.length; i += perTick) {
    if (signal?.aborted) break;
    acc += words.slice(i, i + perTick).join('');
    cb.onDelta(acc);
    await sleep(effort <= 0 ? 24 : WORD_DELAY[effort] * perTick * (0.75 + ((i * 2654435761) % 100) / 220), signal);
  }
  cb.onDone({ aborted: !!signal?.aborted });
}
