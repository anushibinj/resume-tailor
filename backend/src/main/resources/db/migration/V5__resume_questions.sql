-- Single-shot Q&A against a resume: one question in, one answer out.
--
-- There is deliberately no thread/parent column. Every question is answered from the
-- resume alone -- earlier questions and answers in this table are never replayed back to
-- the model as context -- so this is a flat history log, not a conversation.
CREATE TABLE resume_questions (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    resume_id         UUID         NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    llm_profile_id    UUID         REFERENCES llm_profiles (id) ON DELETE SET NULL,
    question          TEXT         NOT NULL,
    answer            TEXT,
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    error_message     TEXT,
    model_used        VARCHAR(255),
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_resume_questions_owner_resume ON resume_questions (owner_id, resume_id, created_at DESC);
