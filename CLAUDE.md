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

Every user-owned table has `owner_id UUID NOT NULL` from `V1__init.sql`, and every
service filters by `CurrentUserProvider.currentUserId()`.

- **Never** call `findAll()` / `findById()` on an owned repository from a service.
  Use `findAllByOwnerId…` / `findByIdAndOwnerId`.
- Adding a user-owned table means adding `owner_id` to it in the same migration.
- **The one deliberate exception is `skill_definitions`** (`com.resumetailor.skill`): plain
  descriptions of skills, shared by every user so a term is explained once. It holds
  general knowledge about a term and nothing about any candidate, resume or posting — keep
  it that way, and do not put user-derived text in it. It is the only repository that may
  be queried without an owner.

`com.resumetailor.user.CurrentUserProvider` was the only seam that needed to change to
add real multi-user auth: `SecurityContextUserProvider` reads the id
`com.resumetailor.auth.JwtAuthenticationFilter` put in Spring Security's
`SecurityContext` after validating the app's own session token (issued by
`AuthController`/`JwtService` once a Google Sign-In ID token is verified). Adding it
needed zero schema migration and zero query changes to any existing owned table — see
`V3__add_auth.sql` for the only migration it did need (auth columns on `users` itself).
Every new user is created `Role.NORMAL_USER`; `ORG_ADMIN` and `ADMIN` exist for RBAC but
nothing assigns them yet.

### 2. The LaTeX preamble is never sent to the LLM

`ResumeParser` splits a LaTeX resume into `preamble` / `body` / `documentTail` at
`\begin{document}` and `\end{document}`. Only the **body** goes into a prompt;
`ResumeParser.reassemble()` puts the original preamble back verbatim.

This is structural protection, not a prompt instruction: a badly behaved model can only
damage prose, never the document class, packages or macros that make the file compile.
Do not "simplify" this by sending the whole file.

### 3. Coverage is judged by the model; only the arithmetic is Java's

`GapAnalyzer` (LLM call 3) decides which of the posting's requirements the user's
**original** resume covers, and quotes the resume line behind each "covered" verdict. It
is given the tailored rewrite too, but only to anchor additions in -- never as evidence.
Judging the rewrite let a rewrite that slipped (a Java developer's tagline rewritten to
"Principal ... C | C++ | C#") vouch for its own invention, and every requirement then
read as covered.
`CoverageScorer` only weights and totals those verdicts (`REQUIRED` 3 / `PREFERRED` 2 /
`NICE` 1).

**This replaced a deterministic keyword matcher, deliberately.** Models phrase
requirements descriptively -- "Java (Programming Language)", "application performance
optimization" -- and literal matching reported a Java developer's resume as missing
Java. Coverage is a judgment about meaning. Do not reintroduce string matching as a
"safety net": being wrong in that direction is what made the panel useless.

Evidence is what makes the judgment checkable, so keep it: every covered requirement
shows the quote it rests on.

### 4. Additions are never applied automatically, and can only insert

Every gap comes with an addition the user can apply in one click. They are created
`PROPOSED` and stay there until the user accepts. `tailored_body` always holds the
model's **pristine** rewrite; the document served and exported is that plus whatever is
currently accepted, recomputed on every change (`TailoringService.effectiveBody`).

Additions are anchored: `anchor` is text already in the resume and `insert_text` goes
beside it (`AdditionApplier`). Two properties follow, and both must survive any rewrite
of this code:

- **Insert only.** Accepting can add text but never delete any, so removing an addition
  restores the document exactly.
- **Never into a comment.** Templates keep whole sections commented out; text placed
  there is invisible in the PDF while the UI claims it was added.

### 5. The rewrite never invents; the user may

`Prompts.tailoringSystem()` allows only reorder / reword / re-emphasise / trim. The
rewrite must never add an employer, title, date, degree, tool or metric the resume does
not already show. That includes the headline: the candidate keeps their own title (the
job's role is passed labelled as *not* theirs) and a skills list may shrink or reorder
but never gain an item. `PromptsTest` pins those rules.

Adding something the resume does not support is a separate, explicit act by the user.
The gap analysis offers an addition for **every** uncovered requirement -- including
ones the resume gives no sign of, because the user may want the keyword for an ATS --
and the UI states plainly what is being added and where. Do not add plausibility
filtering to the gap prompt, and do not moralise in the UI copy: this is the user's
resume and their decision. The line that matters is that the model never makes it for
them.

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
| `config`    | CORS, async executor, `@ConfigurationProperties` |
| `common`    | `BaseEntity` / `AuditedEntity`, exceptions, `@RestControllerAdvice`, hashing |
| `user`      | `User`, `Role` (RBAC), `CurrentUserProvider` — **the auth seam** |
| `auth`      | Google Sign-In verification, app JWT issue/validate, `SecurityConfig` |
| `resume`    | Base resumes, format detection, preamble split, section segmentation |
| `jd`        | Job descriptions and the cached analysis |
| `llm`       | LLM profiles, AES-GCM key crypto, OpenAI-compatible client |
| `tailoring` | Prompts, the four-call pipeline, run entities, summary options, gap analysis, diff building |
| `keyword`   | Requirement types, the weighted coverage score, `KeywordNormalizer` |
| `skill`     | The shared (not owner-scoped) glossary of what each skill is |
| `pdf`       | `PdfCompiler` and the Docker TeX implementation |
| `export`    | Download endpoints and stored artifacts |

## How a run works

1. `POST /api/runs` persists a `PENDING` run **snapshotting the resume as it is now**
   (base resumes can be edited later; history must stay faithful) and returns 202.
2. Work starts in `afterCommit` so the async thread can actually see the row.
3. `TailoringRunner` (on `tailoringExecutor`): resolve LLM settings → `JdAnalyzer`
   (call 1, cached per JD and per prompt version) → tailoring call (call 2) → save the
   rewrite → `SummaryWriter` (call 3, the summary at 4-7 lines) → `GapAnalyzer` (call 4) →
   save requirements and their additions. Call 4 also
   explains any requirement missing from the shared glossary (`SkillDefinitionService`);
   only those are asked for, and they are stored after the gaps are saved, in a failure
   that cannot fail the check.
4. The frontend polls `GET /api/runs/{id}` every 2s while either the run or the gap check
   is still working.

The run is marked COMPLETED as soon as the rewrite is saved, before gaps are known: the
rewrite is the slow, expensive part and is worth showing immediately. Gap analysis
carries its own `gaps_status`, so a failed check leaves the rewrite intact and offers a
retry (`POST /api/runs/{id}/gaps`) instead of losing it. `OUTDATED` marks runs whose
coverage came from the retired matcher -- the UI hides those numbers and offers the
re-check rather than showing values known to be wrong.

A re-check judges the **original** resume (anchoring against the pristine rewrite) and is
told separately about additions the user already accepted, so the model can point at one
(`addressedBy`) rather than proposing it again. Judging a document with additions already applied would report the
requirement as covered, detach the addition from it, and leave the requirement stuck as
covered after the addition was removed.

Summary length options work like the gap check: their own `summary_status`, failure
contained (the rewrite stays), retry via `POST /api/runs/{id}/summary`. They are stored as
JSON on `tailoring_runs` and **`tailored_body` is never edited** -- the chosen length is
swapped in by `EffectiveBody` when the document is composed, *before* accepted additions,
so moving the slider cannot erase an addition the user accepted (rule 4). The quoted
summary must be found in the body or the options are rejected; do not guess a location.
Like the rewrite, variants take facts from the original only (rule 5).

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
cp backend/.env.example backend/.env       # then set ENCRYPTION_KEY, GOOGLE_CLIENT_ID, JWT_SECRET
openssl rand -base64 32                    # value for ENCRYPTION_KEY
openssl rand -base64 48                    # value for JWT_SECRET
docker compose up -d postgres
docker build -t resume-tailor-tex docker/tex   # once, for PDF export
./backend/start-dev.sh                     # mvn spring-boot:run with the dev profile
cd frontend && cp .env.example .env.local && pnpm install && pnpm dev
```

`GOOGLE_CLIENT_ID` (backend) and `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (frontend) must be the
same OAuth 2.0 Web client ID from Google Cloud Console, with `http://localhost:3000`
listed as an Authorized JavaScript origin — see the README's "Multi-user and Google
Sign-In" section.

`backend/start-dev.sh` changes into its own directory before starting, so it runs from
anywhere; spring-dotenv then finds `backend/.env`. Keep it location-independent — don't
hardcode an absolute path.

`ENCRYPTION_KEY` is required — `ApiKeyCipher` refuses to start without a valid 32-byte
base64 key, because it encrypts stored LLM API keys. Changing it makes existing stored
keys undecryptable; the user must re-enter them in Settings.

`GOOGLE_CLIENT_ID` and `JWT_SECRET` are required the same way — `GoogleTokenVerifier` and
`JwtService` refuse to start without them, following the same fail-fast pattern as
`ApiKeyCipher`.

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
- `JwtAuthenticationFilter` is deliberately **not** a `@Component`. `@WebMvcTest` slices
  always pull in every bean that implements `Filter`, regardless of
  `@AutoConfigureMockMvc(addFilters = false)` (that flag only skips running the filter
  chain against the request; the bean still has to be constructed). A scanned
  `JwtAuthenticationFilter` would drag `JwtService` — and its required `JWT_SECRET` — into
  every controller-only test slice. `SecurityConfig` constructs it directly as a `@Bean`
  instead, so it never enters component scanning.
- Custom CSS classes in `frontend/src/app/globals.css` (`.document`, `.mark-add`,
  `.mark-cut`) live inside `@layer components`. Unlayered CSS beats every Tailwind
  utility regardless of specificity, so a class declared outside a layer silently ignores
  utilities like `font-mono` applied next to it.
- **Requirements are handed to the gap model as JSON, and matched back leniently.** Shown
  as `- Java (REQUIRED)` lines, models echo the whole line back as the keyword and every
  verdict is lost — the judging is fine, the matching isn't. `normalizeKeyword` also
  strips parentheticals for the same reason. When fewer than half the requirements come
  back matched, the analysis is discarded with an error rather than defaulted: filling in
  the rest reports fabricated gaps that look exactly like real ones, and a user acting on
  them adds text they never needed. `GapAnalyzer` logs the raw reply at DEBUG (on in the
  dev profile) — check it first when a new model behaves oddly.
- The diff view sets Markdown in the serif document face and LaTeX in monospace: LaTeX on
  screen is markup, not prose.
- **`next dev` and `next build` use separate output folders** — `.next-dev` and `.next`,
  chosen by phase in `frontend/next.config.ts`. Sharing `.next` meant the mandatory
  `pnpm build` gate overwrote a running dev server's manifests, and every page then
  returned 500 with `ENOENT … app-paths-manifest.json`. Keep the split; if you change
  it, `.next-dev` must also stay in `.gitignore`, the ESLint ignores (it is generated
  code — linting it fails the gate) and `tsconfig.json`'s `include` (Next rewrites the
  tracked tsconfig on startup if its types path is missing). This applies to Next 15
  only; Next 16 separates the two itself.
- Never proxy the backend through the frontend (no `rewrites`, no route handlers that
  forward). The frontend calls the backend directly with `fetch` at
  `NEXT_PUBLIC_API_BASE_URL`, defaulting to `http://localhost:8080`, and the backend
  allows the frontend origin via CORS. `frontend/.env.example` is committed on purpose —
  the scaffold's `.env*` ignore rule has a `!.env.example` exception for it.
- `<html>` and `<body>` in `frontend/src/app/layout.tsx` both carry
  `suppressHydrationWarning`. `<html>` needs it because next-themes sets the theme class
  before hydration; `<body>` needs it because browser extensions (ColorZilla,
  Grammarly) inject attributes there. It only covers that element's own attributes, one
  level deep — a mismatch in any child is still reported, which was verified. Do not
  spread it onto components to quiet a hydration warning: that hides real bugs.
- **New frontend components should be lazy loaded by default**, using `next/dynamic`
  (this is a Next.js App Router app — `React.lazy`/`Suspense` is the React-only pattern
  and isn't what's used here). Convention, established on `SectionDiffView`,
  `KeywordMargin` and `GoogleSignInButton`: `const Foo = dynamic(() =>
  import("@/components/foo").then((mod) => mod.Foo), { loading: () => <FooSkeleton /> })`
  next to the other imports in the file that uses it, with the fallback matching the
  shape of what it replaces (built from the `Skeleton` primitive in
  `frontend/src/components/ui.tsx` — reuse it rather than adding another one). Not
  everything qualifies — skip it for: anything Next.js already code-splits per route
  (`page.tsx`/`layout.tsx` default exports); components that gate or wrap the whole app
  and must run before their children can (`Providers`, `AuthProvider`, `SiteHeader`);
  small always-rendered primitives with no heavy dependency (`Button`, `Input`, `Tag`,
  `Spinner`, etc. in `ui.tsx`) where a Suspense boundary would only add flicker; and
  non-exported helper components defined and used only inside another component's own
  file (lazy loading needs a real module to `import()` — extracting a private helper
  into its own file just to lazy-load it is a bigger change than the loading behavior is
  worth). Reasonable candidates are components with a real dependency to defer or that
  render conditionally behind a data/auth state.
