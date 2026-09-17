# Resume Tailor — working notes for agents

Tailors a stored base resume to a pasted job description using the user's own
OpenAI-compatible LLM, then shows every edit for review before export.

**Read this whole file before changing code.** Several rules below are load-bearing
product promises, not style preferences — breaking them silently makes the app lie to
its user.

---

## Working agreement

- Always run the tests at the end of any code change and fix failures before doing
  anything else. If a test cannot be fixed immediately, record it in `TODO.md` and
  continue. Both are mandatory:
  - backend — `cd backend && mvn clean test`
  - frontend — `cd frontend && pnpm lint && pnpm build`
- Always update these when a change affects docs, environment variables or architecture:
  - `README.md`
  - `backend/.env.example` and `frontend/.env.example`
  - `product-architecture-flowchart.mmd`
- Before a code change: read `product-architecture-flowchart.mmd` and `README.md`,
  decide whether backend, frontend or both are affected, and do backend first.
- Check `TODO.md` for relevant items and implement them alongside related work, marking
  them done. Skip TODOs unrelated to the change.
- Preserve existing comments. If you change commented code, update the comment. Delete a
  comment only when it has become factually wrong.
- At the end of every change, auto-commit and then state the commit message used. Do not
  wait for approval. **Never add a `Co-Authored-By: Claude` trailer or a
  `Claude-Session:` line** — commit as the user only.

---

## The five rules that must not be broken

### 1. Every query is owner-scoped

v1 is single-user, but the schema is not. Every user-owned table has
`owner_id UUID NOT NULL` from `V1__init.sql`, and every service filters by
`CurrentUserProvider.currentUserId()`.

- **Never** call `findAll()` / `findById()` on an owned repository from a service.
  Use `findAllByOwnerId…` / `findByIdAndOwnerId`.
- Adding a user-owned table means adding `owner_id` to it in the same migration.

`com.resumetailor.user.CurrentUserProvider` is the only seam that needs to change for
multi-user (v2): delete `SingleUserProvider` and provide a bean that reads Spring
Security's `SecurityContext`. No schema migration, no query changes.

### 2. The LaTeX preamble is never sent to the LLM

`ResumeParser` splits a LaTeX resume into `preamble` / `body` / `documentTail` at
`\begin{document}` and `\end{document}`. Only the **body** goes into a prompt;
`ResumeParser.reassemble()` puts the original preamble back verbatim.

This is structural protection, not a prompt instruction: a badly behaved model can only
damage prose, never the document class, packages or macros that make the file compile.
Do not "simplify" this by sending the whole file.

### 3. Keyword presence is computed in Java, never taken from the model

The LLM extracts *which* keywords a posting asks for (`JdAnalyzer`). Whether the resume
contains them is decided by `KeywordMatcher` in Java, with token-boundary matching and a
weighted score (`REQUIRED` 3 / `PREFERRED` 2 / `NICE` 1).

The match score is the number the user trusts when deciding to hit send. A model must
never grade its own rewrite.

### 4. Suggestions are never applied automatically

`run_suggestions` rows are created `PROPOSED` and stay there. `tailored_body` always
holds the model's **pristine** output; the document the user downloads is computed as
pristine + accepted suggestions via `SuggestionSplicer`, recomputed on every status
change (`TailoringService.rebuildTailoredDocument`).

That is what makes accepting reversible — un-accepting genuinely removes the text again.
Never mutate `tailored_body` in place.

### 5. The tailoring prompt forbids invention

`Prompts.tailoringSystem()` allows only reorder / reword / re-emphasise / trim, and
forbids inventing employers, titles, dates, degrees, tools or metrics. Anything the job
wants that the resume does not support must go to `suggestions`, not into the resume.

If you touch that prompt, keep the absolute rules intact and keep the JSON contract in
sync with `LlmResponseParser`.

---

## Layout

```
backend/    Spring Boot 3.5.5, Java 17, Maven, feature-based packages
frontend/   Next.js 15 App Router, React 19, Tailwind v4, TanStack Query
docker/tex/ Dockerfile for the sandboxed TeX + pandoc image
```

Backend packages under `com.resumetailor` are organised by **feature**, not by layer:

| Package     | Holds |
|-------------|-------|
| `config`    | CORS, async executor, `@ConfigurationProperties`, startup seeding |
| `common`    | `BaseEntity` / `AuditedEntity`, exceptions, `@RestControllerAdvice`, hashing |
| `user`      | `User`, `CurrentUserProvider` — **the v2 auth seam** |
| `resume`    | Base resumes, format detection, preamble split, section segmentation |
| `jd`        | Job descriptions and the cached analysis |
| `llm`       | LLM profiles, AES-GCM key crypto, OpenAI-compatible client |
| `tailoring` | Prompts, async pipeline, run/change/suggestion entities, diff building |
| `keyword`   | Deterministic keyword matching and scoring |
| `pdf`       | `PdfCompiler` and the Docker TeX implementation |
| `export`    | Download endpoints and stored artifacts |

## How a run works

1. `POST /api/runs` persists a `PENDING` run **snapshotting the resume as it is now**
   (base resumes can be edited later; history must stay faithful) and returns 202.
2. Work starts in `afterCommit` so the async thread can actually see the row.
3. `TailoringRunner` (on `tailoringExecutor`): resolve LLM settings → `JdAnalyzer`
   (call 1, cached per JD) → tailoring call (call 2) → `KeywordMatcher` → persist.
4. The frontend polls `GET /api/runs/{id}` every 2s while the status is non-terminal.

Failures are recorded on the run (`status=FAILED`, `error_message`) rather than thrown at
the user, so the message survives a page reload.

## PDF compilation

`DockerTexCompiler` runs the build in a throwaway container. Every flag closes a specific
door and none is decorative — we are compiling markup an LLM wrote seconds earlier, and
LaTeX is a programming language that can read and write files:

- `--network none` — the document cannot phone home.
- `-no-shell-escape` — `\write18` cannot run shell commands.
- `--memory` / `--cpus` — a runaway macro cannot exhaust the host.
- `--security-opt no-new-privileges` — no escalation inside the container.
- a per-run temp dir as the only mount — nothing else on disk is reachable.
- a wall-clock timeout — an infinite TeX loop cannot hang the server.

Engine is chosen by `latexEngineFor()`: `xelatex` when the source uses fontspec /
`\setmainfont` / unicode-math / polyglossia, otherwise `pdflatex`. Markdown goes through
pandoc in the same image.

**A PDF existing is not success.** In `nonstopmode` TeX pushes past errors — an undefined
macro is simply dropped — and still writes a PDF, so content can silently vanish from the
resume the user sends. `compile()` fails on any `!` error line in TeX's `main.log`, even
when a PDF was produced. Warnings (`Overfull \hbox`, `LaTeX Warning`) do not fail. Do not
relax this back to "did a PDF appear".

The stored compile log is TeX's own `main.log`, not the container's terminal output — only
the log file contains the full error context and the `runsystem(...)...disabled.` lines
that prove shell escape was refused.

---

## Local setup

Prerequisites: Java 17, Maven, Node 22, pnpm, Docker Desktop **running**.

```bash
cp backend/.env.example backend/.env       # then set ENCRYPTION_KEY
openssl rand -base64 32                    # value for ENCRYPTION_KEY
docker compose up -d postgres
docker build -t resume-tailor-tex docker/tex   # once, for PDF export
./backend/start-dev.sh                     # mvn spring-boot:run with the dev profile
cd frontend && pnpm install && pnpm dev
```

`backend/start-dev.sh` changes into its own directory before starting, so it runs from
anywhere; spring-dotenv then finds `backend/.env`. Keep it location-independent — don't
hardcode an absolute path.

`ENCRYPTION_KEY` is required — `ApiKeyCipher` refuses to start without a valid 32-byte
base64 key, because it encrypts stored LLM API keys. Changing it makes existing stored
keys undecryptable; the user must re-enter them in Settings.

## Testing

`mvn clean test` **must stay green on a machine with Docker stopped**. Tests needing
Postgres or a real TeX container are tagged `integration` and excluded by surefire:

```bash
cd backend && mvn verify -Pintegration     # adds every *IT class (needs Docker)
```

- `SchemaValidationIT` proves Flyway's schema and the JPA entities agree
  (`ddl-auto: validate` fails startup on any mismatch). Run it after any schema change.
- `DockerTexCompilerIT` compiles real documents in the sandbox image (needs
  `docker build -t resume-tailor-tex docker/tex`) and asserts shell escape is refused and
  a document with TeX errors fails. Run it after touching anything in `pdf`.

Integration tests must be named `*IT` **and** tagged `@Tag("integration")`. Surefire only
picks up `*Test` by default; the `integration` profile adds `**/*IT.java` to its includes.
Without that, the profile silently runs zero integration tests — which happened once.

`ContextWiringTest` boots the full context against H2 on every `mvn test`, so a broken
bean graph fails the default suite rather than surfacing at `spring-boot:run`.

## Gotchas

- `frontend/AGENTS.md` (and the `CLAUDE.md` pointing at it) is generated by the Next.js
  CLI and re-created on `next dev`. It is not project guidance — this file is.
- The frontend reads uploaded resumes in the browser and posts JSON, so the API has no
  multipart endpoints. Keep it that way unless there is a reason not to.
- `run_changes.change_type` is free text normalised by `LlmResponseParser`, not a DB
  enum, because models return varied spellings.
- API keys are write-only over HTTP. `LlmProfileResponse` carries `apiKeyMask` only;
  never add a field that returns the key.
- **Exception handler order matters.** Spring does not pick the most specific handler
  across separate `@RestControllerAdvice` beans; it uses the first bean that matches at
  all. `GlobalExceptionHandler` holds the `Exception` catch-all and is
  `@Order(LOWEST_PRECEDENCE)`; `LlmExceptionHandler` and `PdfExceptionHandler` are
  `HIGHEST_PRECEDENCE`. A new specific handler in its own class needs an `@Order` ahead
  of the catch-all, or it will never run. `ExceptionMappingTest` guards this.
- `OpenAiCompatibleClient` serialises the request body to bytes before sending so the
  request carries a `Content-Length`. Passing the `Map` straight to `.body()` sends a
  chunked body, which small OpenAI-compatible servers and proxies reset the connection on.
- `OpenAiCompatibleClient` has two constructors (one is a test seam), so the production
  one must stay `@Autowired` — without it Spring cannot choose and the app fails to start.
- Custom CSS classes in `frontend/src/app/globals.css` (`.document`, `.mark-add`,
  `.mark-cut`) live inside `@layer components`. Unlayered CSS beats every Tailwind
  utility regardless of specificity, so a class declared outside a layer silently ignores
  utilities like `font-mono` applied next to it.
- The diff view sets Markdown in the serif document face and LaTeX in monospace: LaTeX on
  screen is markup, not prose.
