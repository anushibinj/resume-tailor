# Resume Tailor

Keep your real resume in one place. Paste a job description. Get a copy rewritten to
speak to that posting — with every edit marked up so you can check it before you send it.

The rewrite itself invents nothing: it reorders, rewords, re-emphasises and trims what
you already wrote. Anything the posting asks for that your resume doesn't show is listed
as a gap with an **Add to resume** button, so putting it in is your decision, not the
model's — including a keyword you want purely to get past an ATS.

- **Your model, your key.** Any OpenAI-compatible endpoint — OpenAI, Groq, Ollama,
  LM Studio, vLLM. The key is encrypted before it's stored and never returned by the API.
- **LaTeX and Markdown.** The format is detected from the file. For LaTeX, your preamble
  is held back from the model entirely, so your document class, packages and macros come
  back untouched.
- **Review before you send.** Section-by-section side-by-side diff, a requirements panel
  showing what the posting wants and what you actually cover, and run history.
- **PDF without installing TeX.** Compilation happens inside a throwaway Docker
  container.

---

## Requirements

| Tool | Version used here |
|---|---|
| Java | 17 |
| Maven | 3.9+ |
| Node | 22 |
| pnpm | 10 |
| Docker Desktop | any recent — must be **running** |

## Setup

```bash
git clone <this repo> && cd resume-tailor
```

**1. Configure the backend**

```bash
cp backend/.env.example backend/.env
openssl rand -base64 32          # paste the output as ENCRYPTION_KEY in backend/.env
```

`ENCRYPTION_KEY` is required — it encrypts your stored LLM API keys, and the backend
refuses to start without it.

You also need a Google OAuth client for sign-in (see [Multi-user and Google
Sign-In](#multi-user-and-google-sign-in)):

```bash
openssl rand -base64 48           # paste the output as JWT_SECRET in backend/.env
```

Set `GOOGLE_CLIENT_ID` and `JWT_SECRET` in `backend/.env`, and
`NEXT_PUBLIC_GOOGLE_CLIENT_ID` (the same client ID) in `frontend/.env.local`. The backend
refuses to start without either.

Optionally set `LLM_BASE_URL`, `LLM_API_KEY` and `LLM_MODEL` in the same file to have a
"Default" model profile created for each new user's first sign-in. You can also add one
in the UI later.

**2. Start Postgres**

```bash
docker compose up -d postgres
```

**3. Build the TeX image** (once — only needed for PDF export)

```bash
docker build -t resume-tailor-tex docker/tex
```

**4. Run both sides**

```bash
./backend/start-dev.sh
```

That runs `mvn spring-boot:run` with the `dev` profile, and works from any directory.

```bash
cd frontend && pnpm install && pnpm dev
```

Open <http://localhost:3000>.

## Using it

1. **Sign in** with Google. The first person to sign in with a given Google account gets a
   new `NORMAL_USER` account automatically — nothing to register up front.
2. **Settings** — add your model. "Test" round-trips a tiny prompt so you know the
   credentials work before you spend a real run on them.
3. **Resumes** — upload the `.tex` or `.md` you actually send to employers. Keep several
   if you angle differently for different roles; one is the default.
4. **Tailor** — pick a resume, paste the posting, run it. Takes 20–60 seconds.
5. **Review** — read the diff section by section. The requirements panel shows what the
   posting asks for: hover anything marked covered to see the line of your resume it was
   read from, and use **Add to resume** on the gaps you want to close. Additions land
   inside your existing lists and can be removed again at any point.
6. **Export** — download the `.tex`/`.md`, or compile a PDF.

## How it works

```
Next.js (:3000) ──REST──▶ Spring Boot (:8080) ──▶ Postgres (:5432)
                                 │
                                 ├──▶ your LLM   (OpenAI-compatible /chat/completions)
                                 └──▶ docker run resume-tailor-tex   (pdflatex / pandoc)
```

A run is asynchronous: `POST /api/runs` returns immediately with a `PENDING` run and the
UI polls until it finishes, so a slow model never holds an HTTP connection open.

Three LLM calls per run:

1. **Analyse the posting** → company, role, required/preferred/nice requirements.
   Cached per job description, so re-running costs nothing.
2. **Tailor the resume** → the rewritten body and a per-section change log with reasons.
3. **Check the result** → which requirements your resume covers, with the line of your
   original resume behind each verdict, plus the exact edit that would add each one it
   misses.

Java does the arithmetic on step 3 (required counts 3, preferred 2, nice-to-have 1) but
not the judging. Coverage used to be decided by literal keyword matching, which reported
a Java developer's resume as missing "Java" because the posting called it
"Java (Programming Language)". Whether a resume covers a requirement is a question about
meaning.

Coverage is judged on your **original** resume, not the rewrite. The rewrite is prompted
to keep your own title and never add a skill to a headline, tagline or skills list — but a
model can still slip, and if the rewrite were the thing being judged it could vouch for
its own invention: a Java developer's tagline sprouting "C | C++ | C#" would then report
those as covered. Judged on the original, they show as gaps you can choose to add, and
the rewrite is used only to place the addition.

### How an addition is placed

The model doesn't rewrite your resume to add a skill. It names a snippet already in your
document and the text to put beside it, so "Go" becomes
`…, Kafka \& RabbitMQ, Go}` inside the list you already have, in your template's own
markup. The backend then checks that snippet really exists and isn't inside a commented-out
block. An addition can only insert, never delete — which is why removing one puts the
document back exactly as it was.

### Why the LaTeX preamble never reaches the model

A `.tex` resume is split at `\begin{document}`. Only the body is sent; the preamble is
reattached verbatim afterwards. So even a model that ignores every instruction can only
damage prose — never the thing that makes your document compile.

### Why PDF builds run in Docker

LaTeX is a full programming language that can read and write files, and the markup being
compiled was written by an LLM moments earlier. Each build runs in a throwaway container
with no network, no shell escape, a memory cap and a hard timeout.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/google` | Exchange a Google ID token for the app's session token |
| `GET` | `/api/auth/me` | The signed-in user's profile (id, email, role) |
| `GET/POST` | `/api/resumes` | List, add a base resume |
| `GET/PUT/DELETE` | `/api/resumes/{id}` | Read, update, delete |
| `POST` | `/api/resumes/{id}/default` | Make it the default |
| `POST` | `/api/runs` | Start a tailoring run (202 + run id) |
| `GET` | `/api/runs` | History, paginated |
| `GET` | `/api/runs/{id}` | Poll status, diff, requirement coverage, additions |
| `DELETE` | `/api/runs/{id}` | Delete a run and its compiled PDF |
| `PATCH` | `/api/runs/{id}/suggestions/{sid}` | Add / skip / remove an addition |
| `POST` | `/api/runs/{id}/gaps` | Re-check the requirements with your model |
| `GET` | `/api/runs/{id}/export` | Download the tailored `.tex` / `.md` |
| `POST/GET` | `/api/runs/{id}/pdf` | Compile / download the PDF |
| `GET/POST` | `/api/llm-profiles` | Manage model connections |
| `POST` | `/api/llm-profiles/{id}/test` | Check the endpoint responds |

## Tests

```bash
cd backend && mvn clean test
```

Runs without Docker. Tests that need Postgres or a real TeX container are tagged
`integration`:

```bash
cd backend && mvn verify -Pintegration
```

That adds two test classes, both needing Docker running:

- `SchemaValidationIT` boots the whole app against a real Postgres, which is what proves
  the Flyway schema and the JPA entities agree. Run it after any schema change.
- `DockerTexCompilerIT` compiles real documents in the sandbox image (build it first) and
  checks that shell escape is refused and a document with TeX errors fails.

```bash
cd frontend && pnpm lint && pnpm build
```

## Configuration

Everything lives in `backend/.env` (see `backend/.env.example` for the full list with
comments) and `frontend/.env.local`.

| Variable | Default | Notes |
|---|---|---|
| `ENCRYPTION_KEY` | — | **Required.** base64 32 bytes; encrypts stored API keys |
| `GOOGLE_CLIENT_ID` | — | **Required.** OAuth 2.0 Web client ID; verifies Google Sign-In tokens |
| `JWT_SECRET` | — | **Required.** base64, at least 32 bytes; signs the app's session tokens |
| `JWT_EXPIRATION_MINUTES` | `1440` | How long a session token is valid |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/resume_tailor` | |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | OpenAI defaults | Seeds a profile for each new user's first sign-in |
| `PDF_ENABLED` | `true` | `false` hides PDF export; source download still works |
| `TEX_IMAGE` | `resume-tailor-tex` | Image built from `docker/tex` |
| `PDF_TIMEOUT_SECONDS` | `120` | Hard cap on a compile |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Frontend → backend |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | — | **Required.** Same value as `GOOGLE_CLIENT_ID` |

Changing `ENCRYPTION_KEY` makes already-stored API keys unreadable; re-enter them in
Settings if you do.

## Multi-user and Google Sign-In

v2 has real accounts: sign-in is Google Sign-In only, and every user-owned table has
always carried `owner_id` (`CurrentUserProvider` scopes every query to it), so adding
auth was swapping one bean for a `SecurityContext`-backed implementation — no schema
migration, no query changes. See `CLAUDE.md`.

**Set up a Google OAuth client** (one-time, in [Google Cloud
Console](https://console.cloud.google.com/apis/credentials)):

1. APIs & Services → Credentials → Create Credentials → OAuth client ID → Web application.
2. Add an **Authorized JavaScript origin** for every origin the frontend is served from —
   `http://localhost:3000` for local dev, plus your deployed frontend's origin. Google
   Identity Services refuses to sign in from any other origin.
3. Copy the client ID into `backend/.env`'s `GOOGLE_CLIENT_ID` and
   `frontend/.env.local`'s `NEXT_PUBLIC_GOOGLE_CLIENT_ID` — same value, both places: the
   backend uses it as the audience it verifies every ID token against, the frontend uses
   it to render the Sign-In button.

**How a sign-in works:** the browser gets an ID token straight from Google (Google
Identity Services' own hosted button — the frontend never sees a client secret); the
frontend POSTs it to `POST /api/auth/google`; the backend verifies it against Google's
public keys, finds or creates the user, and returns the app's own session token
(a JWT, `JWT_SECRET`-signed), which the frontend then sends as `Authorization: Bearer`
on every other request. The backend never re-verifies a Google token per request.

**RBAC:** every user created this way starts as `NORMAL_USER`. `ORG_ADMIN` and `ADMIN`
exist on the `Role` enum for future admin features but nothing assigns them yet — for now
promote a user by hand (`UPDATE users SET role = 'ADMIN' WHERE email = '...'`).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Backend won't start, mentions `ENCRYPTION_KEY` | Set it in `backend/.env` — `openssl rand -base64 32` |
| Backend won't start, mentions `GOOGLE_CLIENT_ID` | Set it in `backend/.env` — see [Multi-user and Google Sign-In](#multi-user-and-google-sign-in) |
| Backend won't start, mentions `JWT_SECRET` | Set it in `backend/.env` — `openssl rand -base64 48` |
| Google button shows but sign-in fails / console shows an origin error | The frontend's origin isn't in the OAuth client's Authorized JavaScript origins in Google Cloud Console |
| Signed out unexpectedly | Session tokens expire after `JWT_EXPIRATION_MINUTES` (default 24h) — sign in again |
| `Docker is not running` on PDF export | Start Docker Desktop |
| `The TeX image … is not built yet` | `docker build -t resume-tailor-tex docker/tex` |
| Run fails with a 401 from your provider | Re-enter the key in Settings, then use "Test" |
| Run fails with "truncated JSON" | Raise **Max output tokens** in Settings |
| `Can't reach the backend` in the UI | Backend isn't running, or `NEXT_PUBLIC_API_BASE_URL` is wrong |
| Port 8080 already in use | Set `SERVER_PORT` in `backend/.env` and point `NEXT_PUBLIC_API_BASE_URL` in `frontend/.env.local` at it |
| A document fails to compile | The error names the first TeX error; fix it in the source and recompile. Any TeX error fails the export, even when TeX could have written a PDF — that PDF would be missing content |
| "closed the connection without a usable response" | Your model server dropped the request; check its own logs |
| Every page 500s with `ENOENT … app-paths-manifest.json` | A stale output folder from before dev and build were separated. Stop `pnpm dev`, run `rm -rf frontend/.next frontend/.next-dev`, start it again |
