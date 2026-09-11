/** Small DOM helpers shared by every page. Keeps the markup in index.html and the logic in modules. */

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/** Escapes server-provided text before it is injected into innerHTML (prevents stored XSS). */
export function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

export function setText(selector, text) {
    const el = $(selector);
    if (el) el.textContent = text ?? '';
    return el;
}

export function setHtml(selector, html) {
    const el = $(selector);
    if (el) el.innerHTML = html;
    return el;
}

export function show(selector, visible = true) {
    const el = $(selector);
    if (el) el.style.display = visible ? '' : 'none';
    return el;
}

/** Disables a button and swaps its label while a request is in flight. */
export async function withBusyButton(button, work, busyLabel = 'Working…') {
    if (!button) return work();
    const label = button.textContent;
    button.disabled = true;
    button.textContent = busyLabel;
    button.classList.add('btn-busy');
    try {
        return await work();
    } finally {
        button.disabled = false;
        button.textContent = label;
        button.classList.remove('btn-busy');
    }
}

let toastTimer = null;

export function toast(message, type = 'teal') {
    const el = $('#toast');
    if (!el) return;
    el.textContent = message;
    el.className = `toast toast-${type}`;
    el.style.display = 'block';
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.style.display = 'none'; }, 3500);
}

export const loadingBlock = (label = 'Loading…') =>
    `<div class="state-block"><div class="spinner"></div><span>${esc(label)}</span></div>`;

export const emptyBlock = (title, hint = '') =>
    `<div class="state-block empty"><div class="state-icon">◇</div><div><strong>${esc(title)}</strong>` +
    (hint ? `<div class="state-hint">${esc(hint)}</div>` : '') + `</div></div>`;

export const errorBlock = (message, retryFn = 'lmjRetry') =>
    `<div class="state-block error"><div class="state-icon">!</div><div><strong>Something went wrong</strong>` +
    `<div class="state-hint">${esc(message)}</div>` +
    `<button class="btn btn-secondary btn-sm" onclick="${retryFn}()">Try again</button></div></div>`;

/** Reads a form field and trims it; returns '' when the field is missing. */
export function value(selector) {
    const el = $(selector);
    return el && el.value !== undefined ? String(el.value).trim() : '';
}

/** Selected skill chips inside a container. */
export function selectedSkills(containerSelector) {
    return $$(`${containerSelector} .skill-chip.on`).map(chip => chip.textContent.trim());
}

export function clearSkills(containerSelector) {
    $$(`${containerSelector} .skill-chip`).forEach(chip => {
        chip.classList.remove('on');
        chip.style.background = '';
        chip.style.borderColor = '';
        chip.style.color = '';
    });
}
