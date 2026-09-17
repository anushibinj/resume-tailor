# Resume Tailor

Keep your real resume in one place. Paste a job description. Get a copy rewritten to
speak to that posting — with every edit marked up so you can check it before you send it.

Nothing is invented. The model may reorder, reword, re-emphasise and trim what you
already wrote; anything the job asks for that your resume doesn't support is listed
separately as a suggestion you explicitly accept or dismiss.

- **Your model, your key.** Any OpenAI-compatible endpoint — OpenAI, Groq, Ollama,
  LM Studio, vLLM. The key is encrypted before it's stored and never returned by the API.
- **LaTeX and Markdown.** The format is detected from the file. For LaTeX, your preamble
  is held back from the model entirely, so your document class, packages and macros come
  back untouched.
- **Review before you send.** Section-by-section side-by-side diff, a keyword panel
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

Optionally set `LLM_BASE_URL`, `LLM_API_KEY` and `LLM_MODEL` in the same file to have a
"Default" model profile created on first start. You can also add one in the UI later.

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
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd frontend && pnpm install && pnpm dev
```

Open <http://localhost:3000>.

## Using it

1. **Settings** — add your model. "Test" round-trips a tiny prompt so you know the
   credentials work before you spend a real run on them.
2. **Resumes** — upload the `.tex` or `.md` you actually send to employers. Keep several
   if you angle differently for different roles; one is the default.
3. **Tailor** — pick a resume, paste the posting, run it. Takes 20–60 seconds.
4. **Review** — read the diff section by section. Check the keyword panel for what the
   job wants and you don't cover. Accept any suggestion that's genuinely true of you.
5. **Export** — download the `.tex`/`.md`, or compile a PDF.

## How it works

```
Next.js (:3000) ──REST──▶ Spring Boot (:8080) ──▶ Postgres (:5432)
                                 │
                                 ├──▶ your LLM   (OpenAI-compatible /chat/completions)
                                 └──▶ docker run resume-tailor-tex   (pdflatex / pandoc)
```

A run is asynchronous: `POST /api/runs` returns immediately with a `PENDING` run and the
UI polls until it finishes, so a slow model never holds an HTTP connection open.

Two LLM calls per job description:

1. **Analyse the posting** → company, role, required/preferred/nice keywords,
   responsibilities. Cached per job description, so re-running costs nothing.
2. **Tailor the resume** → the rewritten body, a per-section change log with reasons,
   and suggestions for gaps.

Then **Java** — not the model — checks which keywords actually appear in the result and
computes the weighted match score. A model shouldn't grade its own rewrite.

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
| `GET/POST` | `/api/resumes` | List, add a base resume |
| `GET/PUT/DELETE` | `/api/resumes/{id}` | Read, update, delete |
| `POST` | `/api/resumes/{id}/default` | Make it the default |
| `POST` | `/api/runs` | Start a tailoring run (202 + run id) |
| `GET` | `/api/runs` | History, paginated |
| `GET` | `/api/runs/{id}` | Poll status, diff, keywords, suggestions |
| `PATCH` | `/api/runs/{id}/suggestions/{sid}` | Accept / dismiss / undo a suggestion |
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

`SchemaValidationIT` boots the whole app against a real Postgres, which is what proves
the Flyway schema and the JPA entities agree. Run it after any schema change.

```bash
cd frontend && pnpm lint && pnpm build
```

## Configuration

Everything lives in `backend/.env` (see `backend/.env.example` for the full list with
comments) and `frontend/.env.local`.

| Variable | Default | Notes |
|---|---|---|
| `ENCRYPTION_KEY` | — | **Required.** base64 32 bytes; encrypts stored API keys |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/resume_tailor` | |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | OpenAI defaults | Seeds a profile on first start |
| `PDF_ENABLED` | `true` | `false` hides PDF export; source download still works |
| `TEX_IMAGE` | `resume-tailor-tex` | Image built from `docker/tex` |
| `PDF_TIMEOUT_SECONDS` | `120` | Hard cap on a compile |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Frontend → backend |

Changing `ENCRYPTION_KEY` makes already-stored API keys unreadable; re-enter them in
Settings if you do.

## Multi-user

v1 is deliberately single-user, but the schema isn't. Every owned table carries
`owner_id` and every query filters through `CurrentUserProvider`. Adding real accounts
means swapping one bean for a `SecurityContext`-backed implementation — no migration and
no query changes. See `CLAUDE.md`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Backend won't start, mentions `ENCRYPTION_KEY` | Set it in `backend/.env` — `openssl rand -base64 32` |
| `Docker is not running` on PDF export | Start Docker Desktop |
| `The TeX image … is not built yet` | `docker build -t resume-tailor-tex docker/tex` |
| Run fails with a 401 from your provider | Re-enter the key in Settings, then use "Test" |
| Run fails with "truncated JSON" | Raise **Max output tokens** in Settings |
| `Can't reach the backend` in the UI | Backend isn't running, or `NEXT_PUBLIC_API_BASE_URL` is wrong |
| A document fails to compile | The error names the first TeX error; fix it in the source and recompile |
