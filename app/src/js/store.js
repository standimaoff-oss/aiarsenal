/* Оракул — хранилище состояния (localStorage). Всё на устройстве. */

const CHATS_KEY = 'oracle.chats.v1';
const SETTINGS_KEY = 'oracle.settings.v1';
export const APP_VERSION = '0.3.0-dev.0';
export const MODEL_VERSION = '0.1.0';
export const MODEL_NAME = 'Оракул-Core 0.5B · Q4_K_M';
export const REPO = 'standimaoff-oss/aiarsenal';
export const REPO_URL = `https://github.com/${REPO}`;
// Каналы релизов: приложение и модель разъезжаются тегами
export const APP_TAG_PREFIX = 'app-v';
export const MODEL_TAG_PREFIX = 'model-v';

export const DEFAULT_SETTINGS = {
  theme: 'dark',        // dark | light | system
  accent: 'gold',       // gold | emerald | azure | rose
  fontSize: 'm',        // s | m | l
  alwaysShowTrace: false,
  profileName: '',
  effort: 2,            // индекс уровня размышления
  localModel: false,    // отвечать реальной локальной моделью (нужен скачанный файл весов)
  liveSearch: true,     // отправлять запрос в сеть: DuckDuckGo (через Tauri/прокси) + Wikipedia
  searchProxy: false,   // читать произвольные страницы через allorigins.win (запросы видит сторонний сервис)
};

export function loadSettings() {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    return { ...DEFAULT_SETTINGS, ...(raw ? JSON.parse(raw) : {}) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(s) {
  try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(s)); } catch { /* диск полон — не молчим, но и не падаем */ }
}

export function loadChats() {
  try {
    const raw = localStorage.getItem(CHATS_KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

export function saveChats(chats) {
  try { localStorage.setItem(CHATS_KEY, JSON.stringify(chats)); } catch { /* ignore */ }
}

export function chatStats(chats) {
  let messages = 0;
  for (const c of chats) messages += (c.messages || []).length;
  let bytes = 0;
  try { bytes = new Blob([JSON.stringify(chats)]).size; } catch { bytes = JSON.stringify(chats).length; }
  return { chats: chats.length, messages, bytes };
}

export function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

export function fmtTime(ts) {
  const d = new Date(ts);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  if (sameDay) return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  const y = new Date(now); y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return 'Вчера';
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' });
}

export function groupLabel(ts) {
  const d = new Date(ts);
  const now = new Date();
  const startOf = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diffDays = Math.floor((startOf(now) - startOf(d)) / 86400000);
  if (diffDays <= 0) return 'Сегодня';
  if (diffDays === 1) return 'Вчера';
  if (diffDays < 7) return 'Предыдущие 7 дней';
  return 'Ранее';
}

export function exportChatsJSON(chats) {
  const blob = new Blob([JSON.stringify(chats, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'oracle-chats.json';
  a.click();
  URL.revokeObjectURL(url);
}
