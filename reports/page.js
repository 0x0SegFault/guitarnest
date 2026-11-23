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
