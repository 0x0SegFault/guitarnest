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
