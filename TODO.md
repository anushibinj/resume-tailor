# TODO

Open items, roughly in priority order. Mark done rather than deleting, so the history of
decisions stays readable.

## Already verified

- 108 backend tests pass with Docker stopped; 113 with `mvn verify -Pintegration`.
- `SchemaValidationIT` passes against real Postgres 16 — Flyway's schema and every JPA
  entity agree under `ddl-auto: validate`.
- The whole stack was run end to end (real backend, real Postgres, a stand-in
  OpenAI-compatible server): 48 checks covering key encryption, format detection, the
  preamble never appearing in any prompt the model received, keyword scoring, accepting /
  undoing suggestions, JD-analysis caching, run-history snapshots, and failure paths.
- Real PDFs compiled for both a `.tex` and a `.md` resume, and were inspected: the custom
  macro defined in the LaTeX preamble rendered, accepted suggestions appeared, `50%`
  escaped correctly.
- The PDF sandbox was attacked directly: `\write18` is refused (and TeX's default
  restricted mode *would* have run a whitelisted command without `-no-shell-escape`),
  the container has no network, and no containers are left behind.
- The UI was checked against live data in light and dark themes.

## Verification still owed

- [ ] **A run against your real model.** Everything above used a stand-in server, which
      proves the plumbing but says nothing about how well a given model tailors. Add your
      profile in Settings, use "Test", then tailor against a real posting.
- [ ] **Your own resume's document class.** The `texlive/texlive:latest-medium` base
      compiled a standard `article` resume. If yours uses a class it lacks, the PDF export
      reports the missing file — switch the base in `docker/tex/Dockerfile` to
      `texlive/texlive:latest` and rebuild.

## v2 — multi-user

- [x] Registration and login. Google Sign-In issues the app's own JWT; `SingleUserProvider`
      is replaced by `SecurityContextUserProvider`. The schema already carried `owner_id`
      everywhere, so no query changes were needed.
- [x] RBAC scaffolding. `Role` (`NORMAL_USER` / `ORG_ADMIN` / `ADMIN`) on `User`,
      `@EnableMethodSecurity` wired in `SecurityConfig`. Every new user is `NORMAL_USER`;
      nothing promotes a user yet and no endpoint is role-gated (there's nothing
      admin-only to gate).
- [ ] An actual admin surface once there's something for `ORG_ADMIN`/`ADMIN` to do —
      right now promoting a user means a manual `UPDATE users SET role = ...`.
- [ ] Rate limiting per user on run creation.
- [ ] Move per-user LLM keys behind a per-user encryption key rather than one global one.

## Features worth considering

- [ ] Cover letter generation from the same resume + JD pair.
- [ ] Fetch a posting from a URL instead of pasting it.
- [ ] Stream tokens during a run instead of polling for status.
- [ ] Re-run a past run against an edited base resume, keeping both for comparison.
- [ ] Export straight to Overleaf.
- [ ] Let the user edit the tailored source in-app before export.

## Smaller cleanups

- [ ] `SectionSegmenter` only knows the common LaTeX sectioning macros. Add any your own
      template uses if sections come out lumped together.
- [ ] `KeywordMatcher` tolerates a trailing "s" only. If plurals like "indices" or
      "analyses" matter for your field, revisit it.
- [ ] Run history has no search or filter — fine at low volume, worth adding past ~50 runs.
