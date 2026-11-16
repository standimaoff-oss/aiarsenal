/* ═══════════════════════════════════════════════════════════
   Оракул — точка входа и оркестрация UI.
   ═══════════════════════════════════════════════════════════ */
import {
  APP_VERSION, MODEL_VERSION, MODEL_NAME, REPO, REPO_URL,
  APP_TAG_PREFIX, MODEL_TAG_PREFIX,
  loadSettings, saveSettings, loadChats, saveChats, chatStats,
  uid, fmtTime, groupLabel, exportChatsJSON,
} from './store.js';
import { EFFORTS, runAgent } from './agent.js';

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const escapeHtml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

const state = {
  settings: loadSettings(),
  chats: loadChats(),
  currentId: null,
  generating: false,
  abort: null,
  armedDeleteId: null,
};

const messagesEl = $('#messages');
const threadEl = $('#thread');
const welcomeEl = $('#welcome');
const chatListEl = $('#chatList');
const promptEl = $('#prompt');
const sendBtn = $('#sendBtn');
const stopBtn = $('#stopBtn');
const effortBtn = $('#effortBtn');
const effortPop = $('#effortPop');
const effortLabel = $('#effortLabel');
const effortDots = $$('#effortDots .dot');

/* ───────────────────────── Markdown (минимальный и безопасный) ───────────────────────── */
function renderContent(md) {
  let t = escapeHtml(md);
  t = t.replace(/`([^`]+)`/g, '<code>$1</code>');
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  const lines = t.split('\n');
  let html = '', inList = false, para = [];
  const flushPara = () => { if (para.length) { html += `<p>${para.join('<br>')}</p>`; para = []; } };
  const closeList = () => { if (inList) { html += '</ul>'; inList = false; } };
  for (const line of lines) {
    const li = line.match(/^\s*[-•]\s+(.*)$/);
    const h = line.match(/^#{1,3}\s+(.*)$/);
    if (li) { flushPara(); if (!inList) { html += '<ul>'; inList = true; } html += `<li>${li[1]}</li>`; continue; }
    if (h) { flushPara(); closeList(); html += `<p><strong>${h[1]}</strong></p>`; continue; }
    if (line.trim() === '') { flushPara(); closeList(); continue; }
    closeList(); para.push(line);
  }
  flushPara(); closeList();
  return html;
}

/* ───────────────────────── Тема / акцент / шрифт ───────────────────────── */
const systemMedia = window.matchMedia('(prefers-color-scheme: light)');
function resolvedTheme() {
  if (state.settings.theme === 'system') return systemMedia.matches ? 'light' : 'dark';
  return state.settings.theme;
}
function applyAppearance() {
  const html = document.documentElement;
  html.dataset.theme = resolvedTheme();
  html.dataset.accent = state.settings.accent;
  html.dataset.fontsize = state.settings.fontSize;
}
systemMedia.addEventListener?.('change', () => { if (state.settings.theme === 'system') applyAppearance(); });

/* ───────────────────────── Сайдбар ───────────────────────── */
function visibleChats() {
  const q = $('#chatSearch').value.trim().toLowerCase();
  const sorted = [...state.chats].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  if (!q) return sorted;
  return sorted.filter(c => (c.title || '').toLowerCase().includes(q) ||
    (c.messages || []).some(m => (m.text || '').toLowerCase().includes(q)));
}

function renderSidebar() {
  const list = visibleChats();
  chatListEl.innerHTML = '';
  if (!list.length) {
    const empty = document.createElement('div');
    empty.className = 'chat-empty';
    empty.textContent = $('#chatSearch').value.trim() ? 'Ничего не нашлось. Попробуйте другое слово.' : 'Пока пусто. Задайте первый вопрос — чат появится здесь.';
    chatListEl.appendChild(empty);
    return;
  }
  let currentGroup = null;
  for (const chat of list) {
    const g = groupLabel(chat.updatedAt || chat.createdAt || Date.now());
    if (g !== currentGroup) {
      currentGroup = g;
      const lab = document.createElement('div');
      lab.className = 'chat-group-label';
      lab.textContent = g;
      chatListEl.appendChild(lab);
    }
    const item = document.createElement('button');
    item.className = 'chat-item' + (chat.id === state.currentId ? ' active' : '');
    item.dataset.id = chat.id;
    if (state.armedDeleteId === chat.id) item.classList.add('armed');
    item.innerHTML = `
      <span class="chat-item-title">${escapeHtml(chat.title || 'Новый чат')}</span>
      <span class="chat-item-time">${state.armedDeleteId === chat.id ? 'Удалить?' : fmtTime(chat.updatedAt || chat.createdAt)}</span>
      <span class="chat-item-del" role="button" tabindex="-1" aria-label="Удалить чат">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M6.5 6l1.2 14a2 2 0 0 0 2 1.9h4.6a2 2 0 0 0 2-1.9L17.5 6"/></svg>
      </span>`;
    item.addEventListener('click', (e) => {
      const del = e.target.closest('.chat-item-del');
      if (del) deleteChat(chat.id);
      else openChat(chat.id);
    });
    chatListEl.appendChild(item);
  }
}

function currentChat() { return state.chats.find(c => c.id === state.currentId) || null; }
function persistChats() { saveChats(state.chats); renderSidebar(); }

function deleteChat(id) {
  if (state.armedDeleteId !== id) {
    state.armedDeleteId = id;
    renderSidebar();
    setTimeout(() => { if (state.armedDeleteId === id) { state.armedDeleteId = null; renderSidebar(); } }, 2600);
    return;
  }
  state.armedDeleteId = null;
  state.chats = state.chats.filter(c => c.id !== id);
  if (state.currentId === id) showWelcome();
  persistChats();
  toast('Чат удалён');
}

function setThreadTitle(t) { $('#threadTitle').textContent = t || 'Новый чат'; }

function showWelcome() {
  state.currentId = null;
  threadEl.classList.add('hidden');
  welcomeEl.classList.remove('hidden');
  setThreadTitle('Новый чат');
  renderSidebar();
}

function showThread() {
  welcomeEl.classList.add('hidden');
  threadEl.classList.remove('hidden');
}

function openChat(id) {
  state.currentId = id;
  const chat = currentChat();
  if (!chat) { showWelcome(); return; }
  showThread();
  setThreadTitle(chat.title);
  messagesEl.innerHTML = '';
  for (const msg of chat.messages || []) renderStoredMessage(msg);
  scrollToBottom(true);
  renderSidebar();
  closeMobileSidebar();
}

function newChat() {
  state.currentId = null;
  messagesEl.innerHTML = '';
  showWelcome();
  closeMobileSidebar();
  promptEl.focus();
}

function ensureChatFor(promptText) {
  let chat = currentChat();
  if (chat) return chat;
  const now = Date.now();
  const title = promptText.trim().replace(/\s+/g, ' ').slice(0, 52) + (promptText.trim().length > 52 ? '…' : '');
  chat = { id: uid(), title: title || 'Новый чат', createdAt: now, updatedAt: now, messages: [] };
  state.chats.unshift(chat);
  state.currentId = chat.id;
  setThreadTitle(chat.title);
  return chat;
}

/* ───────────────────────── Сообщение → DOM ───────────────────────── */
const LOGO_SVG = '<svg class="role-mark" viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="27" r="13" fill="none" stroke="var(--accent)" stroke-width="3"/><circle cx="24" cy="27" r="2.7" fill="var(--accent)"/></svg>';

function scrollToBottom(force = false) {
  const nearBottom = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight < 160;
  if (force || nearBottom) {
    messagesEl.scrollTop = messagesEl.scrollHeight;
    $('#scrollPill').classList.add('hidden');
  } else {
    $('#scrollPill').classList.remove('hidden');
  }
}
messagesEl.addEventListener('scroll', () => {
  const nearBottom = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight < 160;
  if (nearBottom) $('#scrollPill').classList.add('hidden');
});
$('#scrollPill').addEventListener('click', () => { messagesEl.scrollTo({ top: messagesEl.scrollHeight, behavior: 'smooth' }); });

function renderUserMessage(text) {
  const div = document.createElement('div');
  div.className = 'message user';
  div.innerHTML = `<div class="bubble">${escapeHtml(text)}</div>`;
  messagesEl.appendChild(div);
  scrollToBottom(true);
}

function hashHue(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0) % 360; }
function tileLetter(url) {
  try {
    const host = new URL(url).hostname;
    const parts = host.split('.');
    const core = parts.length >= 3 ? parts[parts.length - 2] : parts[0];
    return (core[0] || '?').toUpperCase();
  } catch { return '?'; }
}

function renderStepDetail(step) {
  const d = step.detail || {};
  if (d.type === 'search') {
    const rows = (d.results || []).map(r => `
      <div class="src">
        <span class="src-tile" style="background:hsl(${hashHue(r.url)},45%,62%)">${tileLetter(r.url)}</span>
        <span class="src-body">
          <a class="src-title" href="${escapeHtml(r.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(r.title)}</a>
          <span class="src-url">${escapeHtml(r.url)}</span>
          ${r.read ? `<span class="src-read">прочитано · ${r.kb} КБ</span>` : ''}
        </span>
      </div>`).join('');
    return `<div class="src-list">${rows}</div>
      <div class="src-foot">Найдено ${(d.results || []).length} сайтов${d.note ? ` · ${escapeHtml(d.note)}` : ''}</div>`;
  }
  if (d.type === 'read') {
    return `<div class="detail-text">Загружено и очищено ${d.items} стр. (~${d.kb} КБ): вырезаны меню, скрипты и счётчики, извлечён основной текст. Ключевые факты сверены с выдачей поиска.</div>`;
  }
  if (d.type === 'calc') {
    return `<span class="detail-calc">${escapeHtml(d.expression)} = ${escapeHtml(d.result)}</span>`;
  }
  if (d.type === 'text') {
    return `<div class="detail-text">${escapeHtml(d.text)}</div>`;
  }
  return '';
}

const STEP_ICONS = {
  search: '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.3" y2="16.3"/></svg>',
  read: '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
  calc: '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="12" x2="8.01" y2="12"/><line x1="12" y1="12" x2="12.01" y2="12"/><line x1="16" y1="12" x2="16.01" y2="12"/><line x1="8" y1="16" x2="8.01" y2="16"/><line x1="12" y1="16" x2="12.01" y2="16"/><line x1="16" y1="16" x2="16.01" y2="16"/></svg>',
  plan: '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="M9 13l2 2 4-4"/></svg>',
  draft: '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L11 15l-4 1 1-4 8.5-8.5z"/></svg>',
  check: '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>',
};
const CHECK_SVG = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>';

function createStepElement(step) {
  const el = document.createElement('div');
  el.className = 'step running';
  el.innerHTML = `
    <button class="step-head" aria-expanded="false">
      <span class="step-icon">${STEP_ICONS[step.icon] || STEP_ICONS.plan}</span>
      <span class="step-title">${escapeHtml(step.title)}</span>
      <span class="step-state"><span class="spinner"></span></span>
      <svg class="step-chev" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>
    </button>
    <div class="step-detail">${renderStepDetail(step)}</div>`;
  $('.step-head', el).addEventListener('click', () => {
    el.classList.toggle('open');
    $('.step-head', el).setAttribute('aria-expanded', el.classList.contains('open') ? 'true' : 'false');
  });
  return el;
}

function finishStepElement(el) {
  el.classList.remove('running');
  el.classList.add('done');
  $('.step-state', el).innerHTML = CHECK_SVG;
}

function renderStoredMessage(msg) {
  if (msg.role === 'user') { renderUserMessage(msg.text); return; }
  const wrap = document.createElement('div');
  wrap.className = 'message assistant';
  wrap.innerHTML = `<div class="role-line">${LOGO_SVG}<span>Оракул</span></div>`;
  const trace = msg.trace && msg.trace.steps && msg.trace.steps.length ? msg.trace : null;
  if (trace) wrap.appendChild(buildTraceWrapper(trace.steps, state.settings.alwaysShowTrace));
  const content = document.createElement('div');
  content.className = 'content';
  content.innerHTML = renderContent(msg.text || '');
  wrap.appendChild(content);
  if (msg.stopped) {
    const note = document.createElement('div');
    note.className = 'stopped-note';
    note.textContent = 'Остановлено вами';
    wrap.appendChild(note);
  }
  messagesEl.appendChild(wrap);
}

function buildTraceWrapper(steps, open) {
  const trace = document.createElement('div');
  trace.className = 'trace' + (open ? ' open' : '');
  trace.innerHTML = `
    <button class="trace-summary" aria-expanded="${open ? 'true' : 'false'}">
      <svg viewBox="0 0 48 48" width="17" height="17" aria-hidden="true"><circle cx="24" cy="27" r="12" fill="none" stroke="var(--accent)" stroke-width="3.4"/><circle cx="24" cy="27" r="3" fill="var(--accent)"/></svg>
      Ход работы · <b>${steps.length} ${pluralSteps(steps.length)}</b>
      <svg class="chev" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>
    </button>
    <div class="trace-body"></div>`;
  const body = $('.trace-body', trace);
  for (const step of steps) {
    const el = createStepElement(step);
    finishStepElement(el);
    body.appendChild(el);
  }
  $('.trace-summary', trace).addEventListener('click', () => {
    trace.classList.toggle('open');
    $('.trace-summary', trace).setAttribute('aria-expanded', trace.classList.contains('open') ? 'true' : 'false');
  });
  return trace;
}
const pluralSteps = (n) => (n % 10 === 1 && n % 100 !== 11) ? 'шаг' : ([2, 3, 4].includes(n % 10) && ![12, 13, 14].includes(n % 100)) ? 'шага' : 'шагов';

/* ───────────────────────── Отправка и генерация ───────────────────────── */
function setGenerating(on) {
  state.generating = on;
  sendBtn.disabled = on;
  stopBtn.classList.toggle('hidden', !on);
  sendBtn.classList.toggle('hidden', on);
}

async function send(textRaw) {
  const text = String(textRaw ?? promptEl.value).trim();
  if (!text || state.generating) return;

  const chat = ensureChatFor(text);
  showThread();
  renderUserMessage(text);
  const now = Date.now();
  chat.messages.push({ role: 'user', text, ts: now });
  chat.updatedAt = now;
  persistChats();

  promptEl.value = '';
  autogrow();

  // Ассистент: каркас
  const wrap = document.createElement('div');
  wrap.className = 'message assistant streaming';
  wrap.innerHTML = `<div class="role-line">${LOGO_SVG}<span>Оракул</span></div>`;
  const liveTrace = document.createElement('div');
  liveTrace.className = 'trace-live';
  liveTrace.classList.add('hidden');
  wrap.appendChild(liveTrace);
  const content = document.createElement('div');
  content.className = 'content';
  content.innerHTML = '<span class="caret"></span>';
  wrap.appendChild(content);
  messagesEl.appendChild(wrap);
  scrollToBottom(true);

  setGenerating(true);
  state.abort = new AbortController();
  const signal = state.abort.signal;
  const effort = state.settings.effort;
  const liveSteps = [];
  const stepEls = new Map();
  let streamed = '';

  await runAgent({ prompt: text, effort, signal }, {
    onStepStart(step) {
      const el = createStepElement(step);
      stepEls.set(step, el);
      liveSteps.push(step);
      if (liveTrace.classList.contains('hidden')) liveTrace.classList.remove('hidden');
      liveTrace.appendChild(el);
      scrollToBottom(true);
    },
    onStepDone(step) {
      finishStepElement(stepEls.get(step));
      scrollToBottom();
    },
    onDelta(fullText) {
      streamed = fullText;
      content.innerHTML = renderContent(fullText) + '<span class="caret"></span>';
      scrollToBottom();
    },
    onDone({ aborted }) {
      const finalText = streamed.trim();
      content.innerHTML = renderContent(finalText);
      // Сворачиваем живые шаги в аккуратную «ход работы»
      if (liveSteps.length) {
        const traceEl = buildTraceWrapper(liveSteps, state.settings.alwaysShowTrace);
        liveTrace.replaceWith(traceEl);
      } else {
        liveTrace.remove();
      }
      if (aborted) {
        const note = document.createElement('div');
        note.className = 'stopped-note';
        note.textContent = 'Остановлено вами';
        wrap.appendChild(note);
      }
      wrap.classList.remove('streaming');
      chat.messages.push({ role: 'assistant', text: finalText, ts: Date.now(), stopped: aborted || undefined, trace: liveSteps.length ? { effort, steps: liveSteps } : undefined });
      chat.updatedAt = Date.now();
      persistChats();
      setGenerating(false);
      scrollToBottom();
    },
  });
}

$('#composer').addEventListener('submit', (e) => { e.preventDefault(); send(); });
stopBtn.addEventListener('click', () => state.abort?.abort());
promptEl.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); send(); }
});
function autogrow() { promptEl.style.height = 'auto'; promptEl.style.height = Math.min(promptEl.scrollHeight, 220) + 'px'; }
promptEl.addEventListener('input', autogrow);

/* ───────────────────────── Уровень размышления ───────────────────────── */
function paintEffort() {
  const i = state.settings.effort;
  effortLabel.textContent = EFFORTS[i].name;
  effortDots.forEach((d, idx) => d.classList.toggle('on', idx < EFFORTS[i].dots));
  $$('.effort-opt', effortPop).forEach((el, idx) => {
    el.classList.toggle('selected', idx === i);
    el.setAttribute('aria-selected', idx === i ? 'true' : 'false');
  });
}
function renderEffortPop() {
  effortPop.innerHTML = EFFORTS.map((e, i) => `
    <button class="effort-opt" role="option" data-idx="${i}" aria-selected="${i === state.settings.effort}">
      <span class="effort-dots">${EFFORTS[i].dots ? Array.from({ length: 5 }, (_, d) => `<i class="dot${d < e.dots ? ' on' : ''}"></i>`).join('') : ''}</span>
      <span><b>${escapeHtml(e.name)}</b><small>${escapeHtml(e.desc)}</small></span>
      <span class="eo-hint">${escapeHtml(e.hint)}</span>
    </button>`).join('');
  $$('.effort-opt', effortPop).forEach(btn => btn.addEventListener('click', () => {
    state.settings.effort = Number(btn.dataset.idx);
    saveSettings(state.settings);
    paintEffort();
    toggleEffortPop(false);
    promptEl.focus();
  }));
}
function toggleEffortPop(force) {
  const show = force ?? effortPop.classList.contains('hidden');
  effortPop.classList.toggle('hidden', !show);
  effortBtn.setAttribute('aria-expanded', show ? 'true' : 'false');
}
effortBtn.addEventListener('click', (e) => { e.stopPropagation(); toggleEffortPop(); });
document.addEventListener('click', (e) => { if (!e.target.closest('#effortPop') && !e.target.closest('#effortBtn')) toggleEffortPop(false); });

/* ───────────────────────── Подсказки на стартовом экране ───────────────────────── */
const SUGGESTIONS = [
  { icon: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19a8 8 0 1 0-11-11"/><path d="M12 12l-3.5-3.5"/><circle cx="12" cy="12" r="9.2"/></svg>', title: 'Погода в Самаре', sub: 'Агентный ответ: поиск → сайты → раскрывающийся список источников', prompt: 'Какая погода в Самаре?' },
  { icon: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="12" x2="8.01" y2="12"/><line x1="12" y1="12" x2="12.01" y2="12"/><line x1="16" y1="12" x2="16.01" y2="12"/></svg>', title: 'Сколько будет 17 × 23?', sub: 'Точный расчёт — без «галлюцинаций», честная арифметика', prompt: 'Сколько будет 17 × 23?' },
  { icon: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="2"/><ellipse cx="12" cy="12" rx="10" ry="4.2"/><ellipse cx="12" cy="12" rx="10" ry="4.2" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="10" ry="4.2" transform="rotate(120 12 12)"/></svg>', title: 'Квантовая механика за 30 секунд', sub: 'Уровень «Ультра»: план → 15 сайтов → черновик → проверка', prompt: 'Объясни квантовую механику простыми словами' },
  { icon: '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="17" rx="2"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>', title: 'План на день', sub: 'Структура: главное дело утром, блоки 50/10, без телефона до рассвета', prompt: 'Составь мне план на день' },
];
function renderSuggestions() {
  $('#suggestions').innerHTML = SUGGESTIONS.map(s => `
    <button class="sugg" data-prompt="${escapeHtml(s.prompt)}">
      <b><span class="sugg-icon">${s.icon}</span>${escapeHtml(s.title)}</b>
      <span>${escapeHtml(s.sub)}</span>
    </button>`).join('');
  $$('#suggestions .sugg').forEach(b => b.addEventListener('click', () => { state.settings.effort = b.dataset.prompt.includes('Квантовая') ? 4 : state.settings.effort; saveSettings(state.settings); paintEffort(); send(b.dataset.prompt); }));
}

/* ───────────────────────── Настройки: открытие/закрытие ───────────────────────── */
const overlay = $('#overlaySettings');
function openSettings() { overlay.classList.add('open'); overlay.setAttribute('aria-hidden', 'false'); refreshSettingsPanel(); }
function closeSettings() { overlay.classList.remove('open'); overlay.setAttribute('aria-hidden', 'true'); }
$('#settingsBtn').addEventListener('click', openSettings);
$('#profileBtn').addEventListener('click', openSettings);
$('#closeSettings').addEventListener('click', closeSettings);
$$('[data-close-settings]').forEach(el => el.addEventListener('click', closeSettings));

const ACCENTS = [
  { id: 'gold', color: '#d9a441', label: 'Золото' },
  { id: 'emerald', color: '#4cc98a', label: 'Изумруд' },
  { id: 'azure', color: '#5db1ec', label: 'Лазурь' },
  { id: 'rose', color: '#e592a6', label: 'Роза' },
];
function renderSwatches() {
  $('#accentSwatches').innerHTML = ACCENTS.map(a => `<button class="swatch${a.id === state.settings.accent ? ' active' : ''}" data-accent-value="${a.id}" title="${a.label}" role="radio" aria-checked="${a.id === state.settings.accent}" style="background:${a.color}"></button>`).join('');
  $$('#accentSwatches .swatch').forEach(b => b.addEventListener('click', () => {
    state.settings.accent = b.dataset.accentValue;
    saveSettings(state.settings);
    applyAppearance();
    renderSwatches();
  }));
}
function segmentedBind(containerSel, attr, apply) {
  const box = $(containerSel);
  box.addEventListener('click', (e) => {
    const btn = e.target.closest(`[data-${attr}]`);
    if (!btn) return;
    apply(btn.dataset[Object.keys(btn.dataset).find(k => k.endsWith('Value'))]);
  });
}
segmentedBind('#themeSeg', 'theme-value', (v) => { state.settings.theme = v; saveSettings(state.settings); applyAppearance(); syncThemeSeg(); });
segmentedBind('#fontSeg', 'font-value', (v) => { state.settings.fontSize = v; saveSettings(state.settings); applyAppearance(); syncFontSeg(); });
const syncThemeSeg = () => $$('#themeSeg button').forEach(b => b.classList.toggle('active', b.dataset.themeValue === state.settings.theme));
const syncFontSeg = () => $$('#fontSeg button').forEach(b => b.classList.toggle('active', b.dataset.fontValue === state.settings.fontSize));

function initials(name) {
  const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return 'Я';
  return parts.slice(0, 2).map(w => w[0].toUpperCase()).join('');
}
function refreshProfile() {
  const name = state.settings.profileName.trim();
  $('#profileName').textContent = name || 'Локальный пользователь';
  $('#avatarInitial').textContent = initials(name);
  $('#settingsAvatar').textContent = initials(name);
  if ($('#profileNameInput') !== document.activeElement) $('#profileNameInput').value = name;
}
$('#profileNameInput').addEventListener('input', (e) => {
  state.settings.profileName = e.target.value;
  saveSettings(state.settings);
  refreshProfile();
});
$('#traceToggle').addEventListener('change', (e) => {
  state.settings.alwaysShowTrace = e.target.checked;
  saveSettings(state.settings);
  toast(e.target.checked ? 'Ход работы всегда развёрнут' : 'Ход работы сворачивается после ответа');
});

function refreshSettingsPanel() {
  refreshProfile();
  const stats = chatStats(state.chats);
  $('#statChats').textContent = stats.chats;
  $('#statMessages').textContent = stats.messages;
  $('#statBytes').textContent = stats.bytes > 1024 * 1024 ? (stats.bytes / 1048576).toFixed(1) + ' МБ' : Math.max(1, Math.round(stats.bytes / 1024)) + ' КБ';
  $('#traceToggle').checked = !!state.settings.alwaysShowTrace;
  $('#currentVersionText').textContent = APP_VERSION;
  $('#modelVersionText').textContent = `v${MODEL_VERSION} · демо-режим`;
  syncThemeSeg(); syncFontSeg(); renderSwatches();
  disarmClear();
}

$('#footVersion').textContent = 'v' + APP_VERSION;

/* Экспорт / очистка */
$('#exportBtn').addEventListener('click', () => {
  if (!state.chats.length) return toast('История пуста — нечего экспортировать');
  exportChatsJSON(state.chats);
  toast('Чаты сохранены в oracle-chats.json');
});
const clearBtn = $('#clearBtn');
let clearArmed = false, clearTimer = null;
function disarmClear() { clearArmed = false; clearBtn.classList.remove('armed'); clearBtn.textContent = 'Очистить историю чатов'; }
clearBtn.addEventListener('click', () => {
  if (!clearArmed) {
    clearArmed = true;
    clearBtn.classList.add('armed');
    clearBtn.textContent = 'Точно удалить всю историю?';
    clearTimer = setTimeout(disarmClear, 3200);
    return;
  }
  clearTimeout(clearTimer);
  disarmClear();
  state.chats = [];
  saveChats([]);
  showWelcome();
  refreshSettingsPanel();
  toast('История очищена');
});

/* ────────────── Обновления: приложение и модель (GitHub Releases) ────────────── */
const cmpVersions = (a, b) => {
  const pa = String(a).replace(/^(app-|model-)?v/i, '').split('.').map(n => parseInt(n, 10) || 0);
  const pb = String(b).replace(/^(app-|model-)?v/i, '').split('.').map(n => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) { if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) - (pb[i] || 0); }
  return 0;
};
async function fetchJSON(url) {
  const res = await fetch(url, { headers: { Accept: 'application/vnd.github+json' }, signal: AbortSignal.timeout(9000) });
  if (res.status === 404) return { notFound: true };
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}
const setStatus = (el, text, cls) => { el.textContent = text; el.className = 'hint update-status' + (cls ? ' ' + cls : ''); };

$('#appUpdateBtn').addEventListener('click', async (e) => {
  const btn = e.currentTarget;
  const status = $('#appUpdateStatus');
  btn.disabled = true;
  setStatus(status, 'Смотрю на GitHub…');
  try {
    const list = await fetchJSON(`https://api.github.com/repos/${REPO}/releases?per_page=60`);
    const appReleases = Array.isArray(list) ? list.filter(r => (r.tag_name || '').toLowerCase().startsWith(APP_TAG_PREFIX)) : [];
    if (!appReleases.length) { setStatus(status, `Релизов приложения ещё нет — вы на последней версии (${APP_VERSION}).`, 'ok'); }
    else {
      const tag = appReleases[0].tag_name;
      if (cmpVersions(tag, APP_VERSION) > 0) {
        setStatus(status, `Доступна ${tag} (у вас ${APP_VERSION}). Откройте страницу релизов, чтобы скачать.`, 'found');
        const pill = $('#updatePill');
        $('#updatePillVersion').textContent = tag;
        pill.classList.remove('hidden');
      } else { setStatus(status, `У вас последняя версия (${APP_VERSION}).`, 'ok'); }
    }
  } catch {
    setStatus(status, 'Нет связи с GitHub (или исчерпан лимит API). Попробуйте позже — приложение работает офлайн как обычно.', 'err');
  } finally { btn.disabled = false; }
});
$('#releasesLink').href = `${REPO_URL}/releases`;
$('#updatePill').addEventListener('click', () => window.open(`${REPO_URL}/releases`, '_blank', 'noopener'));

$('#modelUpdateBtn').addEventListener('click', async (e) => {
  const btn = e.currentTarget;
  const status = $('#modelUpdateStatus');
  btn.disabled = true;
  setStatus(status, 'Проверяю релизы модели (теги model-v*)…');
  try {
    const list = await fetchJSON(`https://api.github.com/repos/${REPO}/releases?per_page=40`);
    const modelReleases = Array.isArray(list) ? list.filter(r => (r.tag_name || '').toLowerCase().startsWith(MODEL_TAG_PREFIX)) : [];
    if (!modelReleases.length) { setStatus(status, `Релизов модели ещё нет. Текущая: v${MODEL_VERSION} (${MODEL_NAME}). Первая модельная версия появится вместе с Заходом 2.`, 'ok'); }
    else {
      const tag = modelReleases[0].tag_name;
      if (cmpVersions(tag, MODEL_VERSION) > 0) setStatus(status, `Доступно обновление модели: ${tag} (у вас v${MODEL_VERSION}). Скачивание и замена весов — в Заходе 2.`, 'found');
      else setStatus(status, `Модель актуальна: v${MODEL_VERSION}.`, 'ok');
    }
  } catch {
    setStatus(status, 'Нет связи с GitHub (или исчерпан лимит API). Попробуйте позже.', 'err');
  } finally { btn.disabled = false; }
});

/* ───────────────────────── Сайдбар / навигация / горячие клавиши ───────────────────────── */
const appEl = $('#app');
$('#collapseBtn').addEventListener('click', () => appEl.classList.toggle('sidebar-collapsed'));
$('#burger').addEventListener('click', () => appEl.classList.toggle('sidebar-open'));
$('#backdrop').addEventListener('click', () => appEl.classList.remove('sidebar-open'));
function closeMobileSidebar() { appEl.classList.remove('sidebar-open'); }
$('#newChatBtn').addEventListener('click', newChat);
$('#brandHome').addEventListener('click', (e) => { e.preventDefault(); newChat(); });
$('#chatSearch').addEventListener('input', renderSidebar);

document.addEventListener('keydown', (e) => {
  const inField = /^(INPUT|TEXTAREA)$/.test(e.target.tagName) || e.target.isContentEditable;
  if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K' || e.key === 'к' || e.key === 'К')) { e.preventDefault(); newChat(); return; }
  if ((e.ctrlKey || e.metaKey) && (e.key === 'b' || e.key === 'B' || e.key === 'и' || e.key === 'И')) { e.preventDefault(); appEl.classList.toggle('sidebar-collapsed'); return; }
  if (e.key === 'Escape') { closeSettings(); toggleEffortPop(false); return; }
  if (e.key === '/' && !inField) { e.preventDefault(); promptEl.focus(); }
});

/* ───────────────────────── Тосты ───────────────────────── */
let toastTimer = null;
function toast(text) {
  const el = $('#toast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3400);
}

/* ───────────────────────── Старт ───────────────────────── */
applyAppearance();
renderEffortPop();
paintEffort();
renderSuggestions();
refreshProfile();
$('#footVersion').textContent = 'v' + APP_VERSION;
const lastChat = [...state.chats].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))[0];
if (lastChat) openChat(lastChat.id); else showWelcome();
renderSidebar();
autogrow();
