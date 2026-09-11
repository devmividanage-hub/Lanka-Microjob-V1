/** KPI tiles + definition tables rendered from real backend counters. */
import { esc } from '../utils/dom.js';

const TONES = ['teal', 'blue', 'amber', 'purple', 'coral'];

/**
 * @param items [{ label, value, tone?, hint?, action?, key? }]
 */
export function kpiRow(items) {
    return `<div class="kpi-row">${items.map((item, index) => `
        <div class="kpi kpi-${item.tone || TONES[index % TONES.length]}${item.action ? ' kpi-clickable' : ''}"
             ${item.key ? `data-kpi-key="${esc(item.key)}"` : ''}
             ${item.action ? `role="button" tabindex="0" onclick="${item.action}" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();${item.action}}"` : ''}>
            <div class="kpi-n">${esc(item.value)}</div>
            <div class="kpi-l">${esc(item.label)}</div>
            ${item.hint ? `<div class="kpi-hint">${esc(item.hint)}</div>` : ''}
        </div>`).join('')}</div>`;
}

export function sectionTitle(title, actionHtml = '') {
    return `<div class="flex-between section-head">
        <p class="sec-title" style="margin:0">${esc(title)}</p>
        ${actionHtml}
    </div>`;
}

export function dataTable(columns, rows, emptyMessage = 'Nothing to show yet.') {
    if (!rows.length) return `<div class="state-block empty"><div class="state-icon">◇</div><div>${esc(emptyMessage)}</div></div>`;
    return `<div class="jobs-table-wrap"><table class="jobs-table">
        <thead><tr>${columns.map(column => `<th>${esc(column.label)}</th>`).join('')}</tr></thead>
        <tbody>${rows.map(row => `<tr>${columns.map(column => `<td>${column.render(row)}</td>`).join('')}</tr>`).join('')}</tbody>
    </table></div>`;
}
