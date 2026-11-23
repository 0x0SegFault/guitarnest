'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { createChallenge, verifySolution, sha256 } = require('../pow');
const { validate, toIssue } = require('../report');
const { formPage } = require('../page');
const { handle } = require('../handler');

const KEY = 'test-key';
const env = { POW_KEY: KEY, GITHUB_TOKEN: 't', GITHUB_REPO: 'owner/reports' };

function solve(c) {
  for (let n = 0; n <= c.maxnumber; n++) {
    if (sha256(c.salt + n) === c.challenge) return { algorithm: c.algorithm, challenge: c.challenge, number: n, salt: c.salt, signature: c.signature };
  }
  throw new Error('unsolvable');
}

test('a solved puzzle is accepted', () => {
  assert.ok(verifySolution(KEY, solve(createChallenge(KEY, { maxNumber: 1000 }))));
});

test('wrong number, wrong key and expired puzzles are refused', () => {
  const proof = solve(createChallenge(KEY, { maxNumber: 1000 }));
  assert.ok(!verifySolution(KEY, { ...proof, number: proof.number + 1 }));
  assert.ok(!verifySolution('other-key', proof));
  const old = solve(createChallenge(KEY, { maxNumber: 1000, now: Date.now() - 60 * 60 * 1000 }));
  assert.ok(!verifySolution(KEY, old));
  assert.ok(!verifySolution(KEY, null));
});

test('validation trims, limits and rejects', () => {
  assert.equal(validate({ kind: 'spam' }).error, 'kind');
  assert.equal(validate({ kind: 'bug', title: 'hi', details: 'long enough text' }).error, 'title');
  const { report } = validate({ kind: 'bug', title: '  Crash on save  ', details: 'x'.repeat(9000), version: '1.2.0' });
  assert.equal(report.title, 'Crash on save');
  assert.equal(report.details.length, 5000);
});

test('issues get a prefix, labels and the app details', () => {
  const issue = toIssue({ kind: 'feature', title: 'Dark chart', details: 'Please add it', version: '1.2.0', device: 'Pixel' });
  assert.equal(issue.title, 'Idea: Dark chart');
  assert.deepEqual(issue.labels, ['enhancement', 'anonymous']);
  assert.match(issue.body, /App version: 1.2.0/);
});

test('the form escapes pre-filled values', () => {
  const html = formPage({ title: '<script>x</script>', what: '"quoted"' });
  assert.ok(!html.includes('<script>x'));
  assert.ok(html.includes('&lt;script&gt;'));
});

test('a valid report creates one issue; the same puzzle cannot be reused', async () => {
  const calls = [];
  const fetch = async (url, init) => { calls.push({ url, body: JSON.parse(init.body) }); return { ok: true, status: 201 }; };
  const challenge = JSON.parse((await handle({ httpMethod: 'GET', path: '/challenge' }, {}, { env })).body);
  const body = JSON.stringify({ kind: 'bug', title: 'App closes', details: 'It closes when I save.', proof: solve(challenge) });
  const first = await handle({ httpMethod: 'POST', path: '/report', body }, {}, { env, fetch });
  assert.equal(first.statusCode, 200);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://api.github.com/repos/owner/reports/issues');
  assert.equal(calls[0].body.title, 'Bug: App closes');
  const replay = await handle({ httpMethod: 'POST', path: '/report', body }, {}, { env, fetch });
  assert.equal(replay.statusCode, 403);
});

test('missing labels in the repository do not lose the report', async () => {
  const sent = [];
  const fetch = async (url, init) => { const b = JSON.parse(init.body); sent.push(b); return b.labels ? { ok: false, status: 422 } : { ok: true, status: 201 }; };
  const challenge = JSON.parse((await handle({ httpMethod: 'GET', path: '/challenge' }, {}, { env })).body);
  const body = JSON.stringify({ kind: 'feature', title: 'Widget please', details: 'A home screen widget.', proof: solve(challenge) });
  const res = await handle({ httpMethod: 'POST', path: '/report', body }, {}, { env, fetch });
  assert.equal(res.statusCode, 200);
  assert.equal(sent.length, 2);
  assert.equal(sent[1].labels, undefined);
});

test('requests without a proof, bad JSON or other methods are refused', async () => {
  assert.equal((await handle({ httpMethod: 'POST', path: '/report', body: '{"kind":"bug"}' }, {}, { env })).statusCode, 403);
  assert.equal((await handle({ httpMethod: 'POST', path: '/report', body: 'not json' }, {}, { env })).statusCode, 400);
  assert.equal((await handle({ httpMethod: 'DELETE', path: '/' }, {}, { env })).statusCode, 405);
});

test('pages carry strict security headers and the handler never reads request headers', async () => {
  const page = await handle({ httpMethod: 'GET', path: '/', queryStringParameters: { kind: 'feature' },
    get headers() { throw new Error('headers must not be read'); } }, {}, { env });
  assert.equal(page.statusCode, 200);
  assert.match(page.headers['Content-Security-Policy'], /default-src 'none'/);
  assert.equal(page.headers['Referrer-Policy'], 'no-referrer');
  assert.match(page.body, /value="feature" selected/);
});

test('deployment problems are named: missing settings and the GitHub status (never the values)', async () => {
  const report = async (testEnv, fetch) => {
    const challenge = JSON.parse((await handle({ httpMethod: 'GET', path: '/challenge' }, {}, { env: testEnv })).body);
    const body = JSON.stringify({ kind: 'bug', title: 'App closes', details: 'It closes when I save.', proof: solve(challenge) });
    return handle({ httpMethod: 'POST', path: '/report', body }, {}, { env: testEnv, fetch });
  };
  const missing = await report({ POW_KEY: KEY, GITHUB_REPO: 'owner/reports' }, async () => ({ ok: true, status: 201 }));
  assert.equal(missing.statusCode, 500);
  assert.deepEqual(JSON.parse(missing.body), { error: 'config', missing: ['GITHUB_TOKEN'] });
  const denied = await report(env, async () => ({ ok: false, status: 404 }));
  assert.deepEqual(JSON.parse(denied.body), { error: 'upstream', github: 404 });
  assert.ok(!denied.body.includes('t"'));
});

test('follow-up: only a hash goes in the issue; status shows received, published with link, or closed', async () => {
  const { followUpHash, statusOf } = require('../report');
  const code = '0123456789abcdef0123456789abcdef';
  const issues = [];
  const fetch = async (url, init) => {
    if (init && init.method === 'POST') { issues.push(JSON.parse(init.body)); return { ok: true, status: 201 }; }
    const hit = issues.find((i) => decodeURIComponent(url).includes(followUpHash(code)) && i.body.includes(followUpHash(code)));
    return { ok: true, status: 200, json: async () => ({ items: hit ? [{ state: 'open', body: hit.body }] : [] }) };
  };
  const challenge = JSON.parse((await handle({ httpMethod: 'GET', path: '/challenge' }, {}, { env })).body);
  const body = JSON.stringify({ kind: 'bug', title: 'App closes', details: 'It closes when I save.', followUp: code, proof: solve(challenge) });
  assert.equal((await handle({ httpMethod: 'POST', path: '/report', body }, {}, { env, fetch })).statusCode, 200);
  assert.ok(issues[0].body.includes(followUpHash(code)) && !issues[0].body.includes(code));
  const status = await handle({ httpMethod: 'GET', path: '/status', queryStringParameters: { code } }, {}, { env, fetch });
  assert.deepEqual(JSON.parse(status.body), { status: 'received' });
  assert.equal((await handle({ httpMethod: 'GET', path: '/status', queryStringParameters: { code: 'nope' } }, {}, { env, fetch })).statusCode, 400);
  assert.deepEqual(statusOf({ state: 'closed', body: 'x\nPublic issue: https://github.com/o/r/issues/7' }), { status: 'published', public: 'https://github.com/o/r/issues/7' });
  assert.deepEqual(statusOf({ state: 'closed', state_reason: 'not_planned', body: '' }), { status: 'declined' });
});

test('the app preview and the issue have the same text', () => {
  const fs = require('node:fs');
  const kotlin = fs.readFileSync(require('node:path').join(__dirname, '../../shared/src/commonMain/kotlin/app/guitarnest/report/Reports.kt'), 'utf8');
  const footer = /const val FOOTER = "(.*)"/.exec(kotlin)[1];
  const { body } = toIssue({ kind: 'bug', title: 'Closes', details: 'When I save.', version: '0.1.1', device: '' });
  assert.equal(body, `When I save.\n\n---\nApp version: 0.1.1\nDevice: not given\n\n${footer}`);
});
