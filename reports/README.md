# Anonymous reports

A small Scaleway Serverless Function (Paris) that lets users report a problem or suggest a feature **without an account or an email**. Reports become issues in a **private** GitHub repository, where the maintainer reads them before anything is made public.

```
GuitarNest app ──GET /challenge, POST /report──▶ this function ──▶ issue in the private repo 0x0SegFault/guitarnest-reports
(or a browser ──▶ form page at / ──▶ same)        anti-spam puzzle solved on the phone (or in the browser)
```

## Privacy

- The code never reads request headers: it cannot see or store the IP address, the browser or cookies. A test checks this.
- Nothing is logged. Only random puzzle tokens are kept in memory for 15 minutes, to stop re-use.
- Anti-spam is a proof-of-work puzzle solved by the browser (ALTCHA format): no CAPTCHA company, no tracking.
- The page has a strict Content Security Policy, no third-party script and no referrer.
- Hosting: Scaleway (France), region `fr-par`. Like any host, Scaleway's network sees connections; see their privacy policy.

## Files

| File | Role |
|---|---|
| `handler.js` | Routes: `GET /` form, `GET /form.js` script, `GET /challenge` puzzle, `POST /report` |
| `pow.js` | Creates and checks the puzzles |
| `report.js` | Validates a report and turns it into an issue |
| `github.js` | Creates the issue (retries without labels if they are missing) |
| `page.js` | The form page and its script |
| `test/` | `npm test` (Node 18+, no dependencies) |

## Deploy (once)

### 1. GitHub
1. Create a **private** repository `guitarnest-reports`.
2. In it, create the labels `bug`, `enhancement` and `anonymous`.
3. Create a fine-grained token: Settings → Developer settings → Fine-grained tokens.
   - Repository access: **Only select repositories** → `guitarnest-reports`
   - Permissions: **Issues: Read and write** (nothing else)
   - Expiration: 1 year (set a reminder to renew it)

### 2. Scaleway
1. Console → Serverless → Functions → **Create namespace**, region **Paris (fr-par)**.
2. Package the code:
   ```sh
   cd reports && zip function.zip handler.js pow.js report.js github.js page.js package.json
   ```
3. **Create function**: runtime **Node 22**, handler `handler.handle`, upload `function.zip`.
   - Privacy: **Public**. Min scale **0**, max scale **1** (limits cost). Memory: smallest. Timeout: 10 s.
4. Environment:
   - Variable `GITHUB_REPO` = `0x0SegFault/guitarnest-reports`
   - Secret `GITHUB_TOKEN` = the token from step 1
   - Secret `POW_KEY` = a random value: `openssl rand -hex 32`
5. Deploy, open the function URL in a browser, send a test report, and check the private repository.
6. Optional: Billing → set a budget alert (the free tier covers 1 million requests per month).

### 3. Turn it on in the apps
Set `SERVICE_URL` in `shared/src/commonMain/kotlin/app/guitarnest/report/Reports.kt` to the function URL (Android and iPhone use it), then release a new version. The app then offers "Send anonymously" in Settings → Report a problem or idea, and after a crash.

## Triage and follow-up

The app keeps a random follow-up code for each report; the issue holds only its hash. In the app,
"My reports" asks `GET /status?code=…` and shows: Sent, Received, Published (with the public link),
Closed or Declined.

- A good report: read it, remove anything personal, then `reports/publish.sh <number>`. It creates the public
  issue in `0x0SegFault/guitarnest` (without the device line unless `--keep-device`), adds
  "Public issue: <link>" to the private issue and closes it.
- Not planned: close the private issue as "not planned" (`gh issue close <n> -R 0x0SegFault/guitarnest-reports --reason "not planned"`).
- Fixed without publishing: close it as completed.

## Update
Run the tests (`npm test`), package again (step 2), and upload the new `function.zip`.
