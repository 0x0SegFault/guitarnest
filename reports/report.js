'use strict';
// Validation of a report and its conversion to a GitHub issue.

const LIMITS = { title: 120, details: 5000, version: 40, device: 120 };
const KINDS = { bug: ['bug', 'anonymous'], feature: ['enhancement', 'anonymous'] };

function clean(value, max) {
  return String(value ?? '').replace(/\r\n?/g, '\n').trim().slice(0, max);
}

/** @returns {{report?: object, error?: string}} */
function validate(input) {
  if (!input || typeof input !== 'object') return { error: 'invalid' };
  if (!Object.hasOwn(KINDS, input.kind)) return { error: 'kind' };
  const report = {
    kind: input.kind,
    title: clean(input.title, LIMITS.title),
    details: clean(input.details, LIMITS.details),
    version: clean(input.version, LIMITS.version),
    device: clean(input.device, LIMITS.device),
  };
  if (report.title.length < 5) return { error: 'title' };
  if (report.details.length < 10) return { error: 'details' };
  return { report };
}

function toIssue(report) {
  const prefix = report.kind === 'bug' ? 'Bug' : 'Idea';
  const lines = [
    report.details,
    '',
    '---',
    `App version: ${report.version || 'not given'}`,
    `Device: ${report.device || 'not given'}`,
    '',
    '_Sent with the anonymous form. The sender cannot read replies here._',
  ];
  return { title: `${prefix}: ${report.title}`, body: lines.join('\n'), labels: KINDS[report.kind] };
}

module.exports = { validate, toIssue, LIMITS };
