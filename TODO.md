# TODO

Open items, roughly in priority order. Mark done rather than deleting, so the history of
decisions stays readable.

## Verification still owed

- [ ] **End-to-end run against a real model.** The pipeline has not yet been exercised
      against a live LLM — the build was done on a machine where Docker could not be
      started, so Postgres never came up. Work through the checklist in README → Using it.
- [ ] **Run `mvn verify -Pintegration`.** `SchemaValidationIT` is the check that Flyway's
      schema and the JPA entities actually agree. Entity/column names were verified by
      hand but never by Hibernate.
- [ ] **Compile a real PDF.** `docker build -t resume-tailor-tex docker/tex`, then export
      from a finished run — for both a `.tex` and a `.md` resume.
- [ ] Confirm the `texlive/texlive:latest-medium` base carries whatever document class
      your own resume uses. If not, switch the base image in `docker/tex/Dockerfile` to
      `texlive/texlive:latest`.

## v2 — multi-user

- [ ] Registration and login. Replace `SingleUserProvider` with a `SecurityContext`-backed
      `CurrentUserProvider`; the schema already carries `owner_id` everywhere.
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
