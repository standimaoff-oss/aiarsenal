/* ═══════════════════════════════════════════════════════════
   Оракул — локальная модель (Заход 2, эксперимент).

   Ядро: Qwen2.5-0.5B-Instruct в GGUF (Q4_K_M, ≈400 МБ),
   исполнение — wllama (llama.cpp → WebAssembly),
   веса кешируются в Origin Private File System на устройстве,
   после первой загрузки всё работает полностью офлайн.

   Ничего не загрузится без явного действия пользователя.
   ═══════════════════════════════════════════════════════════ */

export const MODEL_SPEC = {
  name: 'Оракул-Core 0.5B',
  base: 'Qwen2.5-0.5B-Instruct · GGUF Q4_K_M',
  url: 'https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf',
  releaseTag: 'model-v0.1.0',
  approxBytes: 420 * 1024 * 1024,
};

const BASE = (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.BASE_URL) || '/';
const WASM_URL = `${BASE}wasm/wllama.wasm`;

/* ── Инъекция фабрики для тестов ── */
let testFactory = null;
export function __setWllamaFactoryForTests(factory) { testFactory = factory; wllamaPromise = null; }

let wllamaPromise = null;
async function getWllama() {
  if (testFactory) return testFactory();
  if (!wllamaPromise) {
    wllamaPromise = (async () => {
      const { Wllama } = await import('@wllama/wllama');
      const w = new Wllama({ default: WASM_URL }, {
        // Только локальный wasm — никаких CDN и внешних библиотек во время работы
        suppressNativeLog: true,
      });
      w.setCompat(null); // без этого wllama по умолчанию тянет compat-ресурсы с CDN
      return w;
    })();
  }
  return wllamaPromise;
}

/**
 * Состояние модели на устройстве: absent | invalid | cached(+size)
 * Ничего не скачивает и не грузит — только смотрит в кэш.
 */
export async function getModelState() {
  try {
    const w = await getWllama();
    const models = await w.modelManager.getModels({ includeInvalid: true });
    const mine = (models || []).find((m) => m.url === MODEL_SPEC.url);
    if (!mine) return { status: 'absent', size: 0 };
    let v = 'invalid';
    try { v = mine.validate(); } catch { v = 'invalid'; }
    if (String(v).toLowerCase() === 'valid') return { status: 'cached', size: mine.size || 0 };
    return { status: 'invalid', size: mine.size || 0 };
  } catch (error) {
    return { status: 'error', size: 0, error: String(error && error.message ? error.message : error) };
  }
}

/**
 * Скачивает веса (один раз). onProgress({loaded, total}) — в байтах.
 * signal — AbortSignal для отмены.
 */
export async function downloadModel({ onProgress, signal } = {}) {
  const w = await getWllama();
  const model = await w.modelManager.getModelOrDownload(
    { url: MODEL_SPEC.url },
    {
      signal,
      progressCallback: ({ loaded, total }) => {
        onProgress?.({ loaded, total: total > 0 ? total : MODEL_SPEC.approxBytes });
      },
    },
  );
  let valid = false;
  try { valid = String(model.validate()).toLowerCase() === 'valid'; } catch { valid = false; }
  return { ok: valid, size: model.size || 0 };
}

/** Удаляет веса с устройства и выгружает контекст. */
export async function removeModel() {
  const w = await getWllama();
  try { await w.exit(); } catch { /* контекст мог не быть загружен */ }
  const models = await w.modelManager.getModels({ includeInvalid: true });
  for (const m of models || []) {
    if (m.url === MODEL_SPEC.url) {
      try { await m.remove(); } catch { /* уже удалена */ }
    }
  }
  wllamaPromise = null;
}

/** Грузит модель в память из кэша. Ничего не скачивает. */
export async function ensureModelLoaded() {
  const w = await getWllama();
  if (typeof w.isModelLoaded === 'function' && w.isModelLoaded()) return w;
  await w.loadModelFromUrl(MODEL_SPEC.url, {
    useCache: true,          // только из кэша; без сети веса не подтянутся молча
    n_ctx: 2048,
    n_threads: Math.max(2, Math.min(8, (typeof navigator !== 'undefined' && navigator.hardwareConcurrency) || 4)),
    warmup: false,
  });
  return w;
}

/* Уровни размышления → реальные рычаги сэмплинга.
   «Мгновенно» — жадный выбор (температура 0), без лишних токенов. */
export const EFFORT_SAMPLING = [
  { temperature: 0.0, top_p: 1.0, max_tokens: 96 },
  { temperature: 0.25, top_p: 0.9, max_tokens: 192 },
  { temperature: 0.4, top_p: 0.92, max_tokens: 384 },
  { temperature: 0.55, top_p: 0.93, max_tokens: 640 },
  { temperature: 0.65, top_p: 0.95, max_tokens: 900 },
];

export const ORACLE_SYSTEM_PROMPT = [
  'Ты — Оракул, локальный русскоязычный ИИ-ассистент.',
  'Отвечай по-русски, кратко и по делу, короткими абзацами и списками.',
  'Если не уверен — прямо скажи об этом, а не выдумывай.',
  'Не придумывай ссылки, источники и цитаты.',
  'Тебя запускают на устройстве пользователя: не проси подключения к интернету.',
].join(' ');

/**
 * Генерация ответа реальной моделью. Потоково: onDelta(fullText).
 * Бросает при ошибке загрузки/инференса — вызывающий код
 * решает, откатиться ли на демо-движок.
 */
export async function generateLocal({ messages, effort = 2, onDelta, signal }) {
  const w = await ensureModelLoaded();
  const s = EFFORT_SAMPLING[effort] || EFFORT_SAMPLING[2];
  const stream = await w.createChatCompletion({
    messages: [{ role: 'system', content: ORACLE_SYSTEM_PROMPT }, ...messages],
    stream: true,
    temperature: s.temperature,
    top_p: s.top_p,
    max_tokens: s.max_tokens,
    abortSignal: signal,
  });
  let full = '';
  for await (const chunk of stream) {
    const delta = chunk?.choices?.[0]?.delta?.content ?? '';
    if (!delta) continue;
    full += delta;
    onDelta?.(full);
  }
  return full;
}
