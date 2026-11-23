'use strict';
// Creates an issue in the private intake repository. Returns { ok, status } (GitHub's HTTP status, for diagnosis).

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
  return { ok: response.ok, status: response.status };
}

/** The intake issue with this follow-up hash, or null (search in the private repository only). */
async function findByFollowUp({ token, repo, hash, fetchImpl = globalThis.fetch }) {
  const q = encodeURIComponent(`repo:${repo} is:issue "follow-up: ${hash}" in:body`);
  const response = await fetchImpl(`https://api.github.com/search/issues?q=${q}&per_page=1`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'guitarnest-reports',
    },
  });
  if (!response.ok) return { ok: false, status: response.status };
  const data = await response.json();
  return { ok: true, issue: (data.items || [])[0] || null };
}

module.exports = { createIssue, findByFollowUp };
