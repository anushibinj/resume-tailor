-- Resume Tailor initial schema.
--
-- MULTI-USER NOTE (v1 is single-user, but the schema is not):
-- every user-owned table carries owner_id NOT NULL from day one, and every query in
-- the application filters by it via CurrentUserProvider. v2 adds authentication by
-- swapping SingleUserProvider for a SecurityContext-backed implementation -- no
-- migration and no query changes. Do not add an owned table without owner_id.

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------- llm_profiles
-- Connection settings for a user-supplied OpenAI-compatible endpoint.
-- api_key_encrypted holds AES-256-GCM ciphertext; the plaintext key never leaves
-- the backend and is never returned by the API (see ApiKeyCipher / LlmProfileMapper).
CREATE TABLE llm_profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(255)  NOT NULL,
    base_url          VARCHAR(1024) NOT NULL,
    api_key_encrypted TEXT,
    api_key_hint      VARCHAR(32),
    model             VARCHAR(255)  NOT NULL,
    temperature       NUMERIC(3, 2) NOT NULL DEFAULT 0.20,
    max_output_tokens INTEGER       NOT NULL DEFAULT 8000,
    is_default        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_llm_profiles_owner_name UNIQUE (owner_id, name)
);
CREATE INDEX idx_llm_profiles_owner ON llm_profiles (owner_id);
-- At most one default profile per user, enforced by the database.
CREATE UNIQUE INDEX uq_llm_profiles_owner_default ON llm_profiles (owner_id) WHERE is_default;

-- --------------------------------------------------------------- resumes
-- A base resume. For LaTeX, preamble/body/document_tail are split on ingest so the
-- preamble is NEVER sent to the LLM (see ResumeParser). For Markdown, preamble and
-- document_tail are empty and body_text == source_text.
CREATE TABLE resumes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    format        VARCHAR(16)  NOT NULL CHECK (format IN ('LATEX', 'MARKDOWN')),
    source_text   TEXT         NOT NULL,
    preamble      TEXT         NOT NULL DEFAULT '',
    body_text     TEXT         NOT NULL,
    document_tail TEXT         NOT NULL DEFAULT '',
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_resumes_owner_name UNIQUE (owner_id, name)
);
CREATE INDEX idx_resumes_owner ON resumes (owner_id);
CREATE UNIQUE INDEX uq_resumes_owner_default ON resumes (owner_id) WHERE is_default;

-- ------------------------------------------------------- job_descriptions
-- content_hash is sha-256 of raw_text; it lets an identical JD reuse a cached
-- analysis instead of paying for the extraction call again.
CREATE TABLE job_descriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company      VARCHAR(255),
    role         VARCHAR(255),
    source_url   VARCHAR(1024),
    raw_text     TEXT          NOT NULL,
    content_hash VARCHAR(64)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_descriptions_owner ON job_descriptions (owner_id);
CREATE INDEX idx_job_descriptions_owner_hash ON job_descriptions (owner_id, content_hash);

-- ----------------------------------------------------------- jd_analyses
-- Cached output of the JD-extraction LLM call. keywords/responsibilities/must_haves
-- are JSON arrays stored as text (no jsonb querying needed; they are read whole).
CREATE TABLE jd_analyses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_description_id  UUID         NOT NULL UNIQUE REFERENCES job_descriptions (id) ON DELETE CASCADE,
    model_used          VARCHAR(255),
    company             VARCHAR(255),
    role                VARCHAR(255),
    keywords            TEXT         NOT NULL,
    responsibilities    TEXT         NOT NULL DEFAULT '[]',
    must_haves          TEXT         NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- -------------------------------------------------------- tailoring_runs
-- original_* columns snapshot the resume as it was at run time: base resumes can be
-- edited afterwards, and run history must stay faithful to what was actually sent.
CREATE TABLE tailoring_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    resume_id           UUID        NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    job_description_id  UUID        NOT NULL REFERENCES job_descriptions (id) ON DELETE CASCADE,
    llm_profile_id      UUID        REFERENCES llm_profiles (id) ON DELETE SET NULL,
    status              VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    format              VARCHAR(16) NOT NULL CHECK (format IN ('LATEX', 'MARKDOWN')),
    original_source     TEXT        NOT NULL,
    original_body       TEXT        NOT NULL,
    preamble            TEXT        NOT NULL DEFAULT '',
    document_tail       TEXT        NOT NULL DEFAULT '',
    tailored_body       TEXT,
    tailored_source     TEXT,
    model_used          VARCHAR(255),
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    match_score         INTEGER,
    error_message       TEXT,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tailoring_runs_owner_created ON tailoring_runs (owner_id, created_at DESC);
CREATE INDEX idx_tailoring_runs_resume ON tailoring_runs (resume_id);

-- ----------------------------------------------------------- run_changes
-- What the model changed, per section -- drives the rationale chips in the diff view.
CREATE TABLE run_changes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        UUID         NOT NULL REFERENCES tailoring_runs (id) ON DELETE CASCADE,
    section_title VARCHAR(512),
    change_type   VARCHAR(32)  NOT NULL,
    rationale     TEXT,
    ordinal       INTEGER      NOT NULL
);
CREATE INDEX idx_run_changes_run ON run_changes (run_id, ordinal);

-- ------------------------------------------------------- run_suggestions
-- Proposed additions. These are NEVER spliced into tailored_source automatically;
-- status stays PROPOSED until the user accepts one in the UI.
CREATE TABLE run_suggestions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id         UUID         NOT NULL REFERENCES tailoring_runs (id) ON DELETE CASCADE,
    kind           VARCHAR(32)  NOT NULL CHECK (kind IN ('BULLET', 'SKILL', 'SUMMARY')),
    target_section VARCHAR(512),
    content        TEXT         NOT NULL,
    rationale      TEXT,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PROPOSED'
                       CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED')),
    ordinal        INTEGER      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_run_suggestions_run ON run_suggestions (run_id, ordinal);

-- ---------------------------------------------------------- run_keywords
-- present_in_* are computed in Java by KeywordMatcher, never taken from the LLM.
CREATE TABLE run_keywords (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID         NOT NULL REFERENCES tailoring_runs (id) ON DELETE CASCADE,
    keyword             VARCHAR(255) NOT NULL,
    importance          VARCHAR(16)  NOT NULL CHECK (importance IN ('REQUIRED', 'PREFERRED', 'NICE')),
    present_in_original BOOLEAN      NOT NULL,
    present_in_tailored BOOLEAN      NOT NULL,
    ordinal             INTEGER      NOT NULL
);
CREATE INDEX idx_run_keywords_run ON run_keywords (run_id, ordinal);

-- ------------------------------------------------------------- artifacts
-- Compiled PDFs on disk, linked to the run that produced them.
CREATE TABLE artifacts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id       UUID          NOT NULL REFERENCES tailoring_runs (id) ON DELETE CASCADE,
    kind         VARCHAR(16)   NOT NULL CHECK (kind IN ('PDF', 'SOURCE')),
    file_path    VARCHAR(1024) NOT NULL,
    content_type VARCHAR(128)  NOT NULL,
    size_bytes   BIGINT        NOT NULL,
    compile_log  TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_artifacts_run ON artifacts (run_id, kind);
