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
