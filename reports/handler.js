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

const { createChallenge, verifySolution } = require('./pow');
const { validate, toIssue, followUpHash, statusOf, FOLLOW_UP } = require('./report');
const { createIssue, findByFollowUp } = require('./github');
const { formPage, formScript } = require('./page');

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
  if (method === 'GET' && name === 'status') {
    // The app asks with its follow-up code; the answer says only received / published (+ public link) / closed.
    const code = String((event.queryStringParameters || {}).code || '');
    if (!FOLLOW_UP.test(code)) return json(400, { error: 'code' });
    const found = await findByFollowUp({ token: env.GITHUB_TOKEN, repo: env.GITHUB_REPO, hash: followUpHash(code), fetchImpl: deps.fetch });
    if (!found.ok) return json(502, { error: 'upstream', github: found.status });
    return found.issue ? json(200, statusOf(found.issue)) : json(404, { status: 'unknown' });
  }
  if (method === 'POST' && name === 'report') {
    const input = readBody(event);
    if (!input) return json(400, { error: 'invalid' });
    if (!verifySolution(env.POW_KEY, input.proof) || !firstUse(input.proof.salt)) return json(403, { error: 'proof' });
    const { report, error } = validate(input);
    if (error) return json(400, { error });
    // Setting problems are named (never their values), so the maintainer can fix the deployment.
    const missing = ['GITHUB_TOKEN', 'GITHUB_REPO', 'POW_KEY'].filter((k) => !env[k]);
    if (missing.length) return json(500, { error: 'config', missing });
    const result = await createIssue({ token: env.GITHUB_TOKEN, repo: env.GITHUB_REPO, issue: toIssue(report), fetchImpl: deps.fetch });
    // 401: token wrong or expired. 403: token without "Issues: write". 404: repository name wrong or not in the token.
    return result.ok ? json(200, { ok: true }) : json(502, { error: 'upstream', github: result.status });
  }
  if (method === 'GET') return reply(200, 'text/html; charset=utf-8', formPage(event.queryStringParameters || {}));
  return json(405, { error: 'method' });
}

module.exports = { handle };
