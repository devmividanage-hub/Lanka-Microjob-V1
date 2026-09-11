/** Application rows for the worker and employer dashboards. */
import { esc } from '../utils/dom.js';
import { applicationStatusBadge, dateTime, money, shortDate, skillsList } from '../utils/format.js';

/** Worker view: which job, what happened, and what they can still do. */
export function workerApplicationCard(application) {
    const status = String(application.status).toUpperCase();
    const canCancel = status === 'APPLIED';
    return `
    <div class="app-card">
      <div class="app-top">
        <div>
          <div class="app-title">${esc(application.jobTitle)}</div>
          <div class="app-sub">${esc(application.employer || 'Employer')} &middot; ${esc(application.city || application.district || '')}
            &middot; ${shortDate(application.jobDate)}</div>
        </div>
        <div class="app-right">
          <div class="app-pay">${money(application.payPerWorker)}<span>/day</span></div>
          ${applicationStatusBadge(status)}
        </div>
      </div>
      <div class="app-foot">
        <span class="mono app-time">Applied ${dateTime(application.appliedAt)}</span>
        ${canCancel
            ? `<button class="btn btn-secondary btn-sm" onclick="lmjCancelApplication(${application.id}, this)">Withdraw</button>`
            : `<span class="app-note">${status === 'ACCEPTED' ? 'You were accepted for this job' : status === 'COMPLETED' ? 'Work completed' : status === 'REJECTED' ? 'Not selected this time' : 'Withdrawn'}</span>`}
      </div>
    </div>`;
}

/** Employer view: who applied, their real skills, and the accept/reject decision. */
export function employerApplicationCard(application) {
    const status = String(application.status).toUpperCase();
    const skills = skillsList(application.workerSkills);
    const pending = status === 'APPLIED';
    const accepted = status === 'ACCEPTED';
    return `
    <div class="app-card">
      <div class="app-top">
        <div>
          <div class="app-title">${esc(application.workerName || 'Worker')}</div>
          <div class="app-sub">${esc(application.jobTitle)} &middot; ${esc(application.city || application.district || '')}
            &middot; ${shortDate(application.jobDate)}</div>
        </div>
        <div class="app-right">${applicationStatusBadge(status)}</div>
      </div>
      <div class="app-tags">
        ${skills.length
            ? skills.map(skill => `<span class="tag tag-purple">${esc(skill)}</span>`).join('')
            : '<span class="tag tag-blue">No skills recorded on profile</span>'}
        ${application.message ? `<span class="app-message">&ldquo;${esc(application.message)}&rdquo;</span>` : ''}
      </div>
      <div class="app-foot">
        <span class="mono app-time">${esc(application.workerEmail || 'no email on file')} &middot; ${dateTime(application.appliedAt)}</span>
        <span class="app-decision">
          ${pending ? `
            <button class="btn btn-primary btn-sm" onclick="lmjAcceptApplication(${application.id}, this)">Accept</button>
            <button class="btn btn-secondary btn-sm" onclick="lmjRejectApplication(${application.id}, this)">Reject</button>` : ''}
          ${accepted ? `<button class="btn btn-purple btn-sm" onclick="lmjCompleteApplication(${application.id}, this)">Mark completed</button>` : ''}
        </span>
      </div>
    </div>`;
}
