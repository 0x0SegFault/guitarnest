'use strict';
// GuitarNest reports: all files in one, for the Scaleway inline editor. Built from reports/*.js; edit those, not this.
const __modules = {};
function __require(name) {
  const key = name.replace('./', '').replace(/\.js$/, '') + '.js';
  if (!__modules[key]) return require(name);
  if (!__modules[key].loaded) { __modules[key].loaded = true; __modules[key].fn(__modules[key].module, __modules[key].module.exports); }
  return __modules[key].module.exports;
}
__modules['pow.js'] = { loaded: false, module: { exports: {} }, fn: (module, exports) => {
'use strict';
// Proof-of-work anti-spam (ALTCHA-compatible format). The browser finds a number whose SHA-256
// with the salt matches the challenge. No cookie, no tracking, no third party.
const crypto = require('node:crypto');

const ALGORITHM = 'SHA-256';
const TTL_SECONDS = 10 * 60;

const sha256 = (text) => crypto.createHash('sha256').update(text).digest('hex');
const hmac = (key, text) => crypto.createHmac('sha256', key).update(text).digest('hex');

function createChallenge(key, { now = Date.now(), maxNumber = 50000 } = {}) {
  const expires = Math.floor(now / 1000) + TTL_SECONDS;
  const salt = `${crypto.randomBytes(12).toString('hex')}?expires=${expires}`;
  const secretNumber = crypto.randomInt(0, maxNumber + 1);
  const challenge = sha256(salt + secretNumber);
  return { algorithm: ALGORITHM, challenge, maxnumber: maxNumber, salt, signature: hmac(key, challenge) };
}

/** True when the solution is correct, signed by us and not expired. */
function verifySolution(key, solution, { now = Date.now() } = {}) {
  if (!solution || typeof solution !== 'object') return false;
  const { algorithm, challenge, number, salt, signature } = solution;
  if (algorithm !== ALGORITHM || typeof salt !== 'string' || !Number.isInteger(number)) return false;
  const match = /\?expires=(\d+)$/.exec(salt);
  if (!match || Number(match[1]) * 1000 < now) return false;
  if (sha256(salt + number) !== challenge) return false;
  const expected = Buffer.from(hmac(key, challenge));
  const given = Buffer.from(String(signature));
  return expected.length === given.length && crypto.timingSafeEqual(expected, given);
}

module.exports = { createChallenge, verifySolution, sha256 };

} };
__modules['report.js'] = { loaded: false, module: { exports: {} }, fn: (module, exports) => {
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

} };
__modules['github.js'] = { loaded: false, module: { exports: {} }, fn: (module, exports) => {
'use strict';
// Creates an issue in the private intake repository.

async function createIssue({ token, repo, issue, fetchImpl = globalThis.fetch }) {
  const send = (body) => fetchImpl(`https://api.github.com/repos/${repo}/issues`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'guitarnest-reports',
    },
    body: JSON.stringify(body),
  });
  let response = await send(issue);
  // Missing labels make GitHub refuse the issue: retry without them rather than lose the report.
  if (response.status === 422 && issue.labels) {
    const { labels, ...withoutLabels } = issue;
    response = await send(withoutLabels);
  }
  return response.ok;
}

module.exports = { createIssue };

} };
__modules['page.js'] = { loaded: false, module: { exports: {} }, fn: (module, exports) => {
'use strict';
// The form page and its script. Served by the function itself: no CDN, no third party.

const escapeHtml = (s) => String(s ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function formPage(query = {}) {
  const kind = query.kind === 'feature' ? 'feature' : 'bug';
  const v = (name, max) => escapeHtml(String(query[name] ?? '').slice(0, max));
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>GuitarNest · anonymous report</title>
<style>
  :root { color-scheme: light dark; --rose: #c2415d; --bg: #fff8f7; --fg: #2a1f21; --muted: #6e5a5e; --line: #ebd6da; }
  @media (prefers-color-scheme: dark) { :root { --bg: #1b1416; --fg: #f0dee1; --muted: #c3aeb2; --line: #3e3134; --rose: #ff8fa6; } }
  body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.5 system-ui, sans-serif; }
  main { max-width: 640px; margin: 0 auto; padding: 24px 16px 48px; }
  h1 { font-size: 1.5rem; margin: 0 0 4px; }
  p.lead { color: var(--muted); margin: 0 0 20px; }
  label { display: block; font-weight: 600; margin: 16px 0 6px; }
  input, textarea, select { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid var(--line);
    border-radius: 12px; background: transparent; color: inherit; font: inherit; }
  textarea { min-height: 180px; }
  .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  @media (max-width: 480px) { .row { grid-template-columns: 1fr; } }
  button { margin-top: 20px; width: 100%; padding: 14px; border: 0; border-radius: 14px; background: var(--rose);
    color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
  button:disabled { opacity: .6; cursor: wait; }
  .note { color: var(--muted); font-size: .9rem; }
  #status { margin-top: 16px; font-weight: 600; }
</style>
</head>
<body>
<main>
  <h1>GuitarNest · anonymous report</h1>
  <p class="lead">No account and no email. Nothing about you is stored: not your IP address, not a cookie.
  Do not paste copyrighted tabs or lyrics. Reports are read by the maintainer before anything is made public.</p>
  <form id="report">
    <label for="kind">Type</label>
    <select id="kind" name="kind">
      <option value="bug"${kind === 'bug' ? ' selected' : ''}>Problem</option>
      <option value="feature"${kind === 'feature' ? ' selected' : ''}>Idea for a feature</option>
    </select>
    <label for="title">Summary</label>
    <input id="title" name="title" maxlength="120" required minlength="5" value="${v('title', 120)}">
    <label for="details">Details</label>
    <textarea id="details" name="details" maxlength="5000" required minlength="10">${v('what', 5000)}</textarea>
    <div class="row">
      <div><label for="version">App version</label><input id="version" name="version" maxlength="40" value="${v('version', 40)}"></div>
      <div><label for="device">Phone</label><input id="device" name="device" maxlength="120" value="${v('device', 120)}"></div>
    </div>
    <button id="send" type="submit">Send</button>
    <p class="note">Before sending, your browser solves a small puzzle for a second or two. It stops spam without tracking you.</p>
    <p id="status" role="status" aria-live="polite"></p>
  </form>
</main>
<script src="form.js"></script>
</body>
</html>`;
}

const formScript = `'use strict';
const form = document.getElementById('report');
const status = document.getElementById('status');
const button = document.getElementById('send');

async function sha256(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

async function solve(c) {
  for (let n = 0; n <= c.maxnumber; n++) {
    if (await sha256(c.salt + n) === c.challenge) {
      return { algorithm: c.algorithm, challenge: c.challenge, number: n, salt: c.salt, signature: c.signature };
    }
  }
  throw new Error('puzzle');
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  button.disabled = true;
  status.textContent = 'Checking you are not a robot…';
  try {
    const challenge = await (await fetch('challenge', { cache: 'no-store' })).json();
    const proof = await solve(challenge);
    status.textContent = 'Sending…';
    const data = Object.fromEntries(new FormData(form));
    const response = await fetch('report', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...data, proof }),
    });
    if (response.ok) {
      form.reset();
      status.textContent = 'Thank you. Your report was sent.';
    } else {
      const { error } = await response.json().catch(() => ({}));
      status.textContent = error === 'title' ? 'Please write a summary (5 characters or more).'
        : error === 'details' ? 'Please add details (10 characters or more).'
        : 'The report could not be sent. Please try again later.';
    }
  } catch (e) {
    status.textContent = 'The report could not be sent. Check your connection and try again.';
  } finally {
    button.disabled = false;
  }
});
`;

module.exports = { formPage, formScript, escapeHtml };

} };
__modules['handler.js'] = { loaded: false, module: { exports: {} }, fn: (module, exports) => {
'use strict';
// Scaleway Serverless Function (Node.js). Anonymous GuitarNest reports -> issues in a private repository.
//
// Environment:
//   GITHUB_TOKEN   fine-grained token, only the intake repository, permission "Issues: write" (secret)
//   GITHUB_REPO    e.g. 0x0SegFault/guitarnest-reports
//   POW_KEY        random secret that signs the anti-spam puzzles (secret)
//
// Privacy: this code never reads request headers, so it never sees or stores the IP address,
// user agent or cookies. Nothing is logged.

const { createChallenge, verifySolution } = __require('./pow');
const { validate, toIssue } = __require('./report');
const { createIssue } = __require('./github');
const { formPage, formScript } = __require('./page');

const MAX_BODY = 20000;

// Each solved puzzle works once. Only random salts are kept, until they expire: no personal data.
const usedSalts = new Map();
function firstUse(salt, now = Date.now()) {
  for (const [key, expires] of usedSalts) if (expires < now) usedSalts.delete(key);
  if (usedSalts.has(salt)) return false;
  usedSalts.set(salt, now + 15 * 60 * 1000);
  return true;
}

const SECURITY_HEADERS = {
  'Cache-Control': 'no-store',
  'Referrer-Policy': 'no-referrer',
  'X-Content-Type-Options': 'nosniff',
  'Content-Security-Policy':
    "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
};

const reply = (statusCode, contentType, body) => ({
  statusCode,
  headers: { ...SECURITY_HEADERS, 'Content-Type': contentType },
  body,
});
const json = (statusCode, value) => reply(statusCode, 'application/json', JSON.stringify(value));

function route(event) {
  const path = String(event.path || '/').replace(/\/+$/, '');
  return path.slice(path.lastIndexOf('/') + 1);
}

function readBody(event) {
  const raw = event.isBase64Encoded ? Buffer.from(event.body || '', 'base64').toString('utf8') : event.body || '';
  if (raw.length > MAX_BODY) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

async function handle(event, context, deps = {}) {
  const env = deps.env || process.env;
  const method = event.httpMethod || 'GET';
  const name = route(event);

  if (method === 'GET' && name === 'form.js') return reply(200, 'text/javascript; charset=utf-8', formScript);
  if (method === 'GET' && name === 'challenge') return json(200, createChallenge(env.POW_KEY));
  if (method === 'POST' && name === 'report') {
    const input = readBody(event);
    if (!input) return json(400, { error: 'invalid' });
    if (!verifySolution(env.POW_KEY, input.proof) || !firstUse(input.proof.salt)) return json(403, { error: 'proof' });
    const { report, error } = validate(input);
    if (error) return json(400, { error });
    const ok = await createIssue({ token: env.GITHUB_TOKEN, repo: env.GITHUB_REPO, issue: toIssue(report), fetchImpl: deps.fetch });
    return ok ? json(200, { ok: true }) : json(502, { error: 'upstream' });
  }
  if (method === 'GET') return reply(200, 'text/html; charset=utf-8', formPage(event.queryStringParameters || {}));
  return json(405, { error: 'method' });
}

module.exports = { handle };

} };
module.exports = __require('./handler');
