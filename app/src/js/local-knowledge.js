/* ═══════════════════════════════════════════════════════════
   BM25 по встроенной базе знаний (офлайн).
   ═══════════════════════════════════════════════════════════ */
import { CORPUS } from './rag-corpus.js';

const STOP = new Set(['и', 'в', 'во', 'не', 'что', 'он', 'на', 'я', 'с', 'со', 'как', 'а', 'то', 'все', 'она', 'так', 'его', 'но', 'да', 'ты', 'к', 'у', 'же', 'вы', 'за', 'бы', 'по', 'только', 'её', 'мне', 'было', 'вот', 'от', 'меня', 'ещё', 'нет', 'о', 'из', 'ему', 'теперь', 'когда', 'даже', 'ну', 'вдруг', 'ли', 'если', 'или', 'ни', 'быть', 'был', 'него', 'до', 'вас', 'нибудь', 'опять', 'уж', 'вам', 'ведь', 'там', 'потом', 'себя', 'ничего', 'ей', 'мочь', 'они', 'тут', 'где', 'весь', 'под', 'мы', 'для', 'кто', 'этот', 'того', 'сам', 'какой', 'совсем', 'любой', 'того', 'ещё', 'больше', 'про', 'свою', 'сих', 'затем', 'чем', 'это', 'раз', 'уже', 'вот']);

const tokenize = (s) => String(s)
  .toLowerCase()
  .replace(/ё/g, 'е')
  .split(/[^a-zа-я0-9]+/i)
  .filter((t) => t.length > 1 && !STOP.has(t));

function buildIndex(corpus) {
  const docs = corpus.map((e) => tokenize(`${e.title} ${e.tags} ${e.text}`));
  const df = new Map();
  for (const tokens of docs) {
    for (const t of new Set(tokens)) df.set(t, (df.get(t) || 0) + 1);
  }
  const N = docs.length;
  const avgLen = docs.reduce((s, d) => s + d.length, 0) / Math.max(1, N);
  return { docs, df, N, avgLen };
}

const INDEX = buildIndex(CORPUS);
const K1 = 1.2, B = 0.75;

/**
 * Возвращает топ-K записей по запросу: [{ title, text, score, id }].
 * Пусто, если ничего не нашлось (порог подобран для коротких документов).
 */
export function searchLocal(query, { topK = 4, minScore = 1.6 } = {}) {
  const q = tokenize(query);
  if (!q.length) return [];
  const scores = new Array(CORPUS.length).fill(0);
  for (const term of q) {
    const df = INDEX.df.get(term) || 0;
    if (!df) continue;
    const idf = Math.log(1 + (INDEX.N - df + 0.5) / (df + 0.5));
    for (let i = 0; i < INDEX.docs.length; i++) {
      const tf = INDEX.docs[i].reduce((acc, t) => acc + (t === term ? 1 : 0), 0);
      if (!tf) continue;
      const len = INDEX.docs[i].length;
      const denom = tf + K1 * (1 - B + B * (len / INDEX.avgLen));
      scores[i] += idf * ((tf * (K1 + 1)) / denom);
    }
  }
  return scores
    .map((score, i) => ({ score, ...CORPUS[i] }))
    .filter((r) => r.score >= minScore)
    .sort((a, b) => b.score - a.score)
    .slice(0, topK);
}
