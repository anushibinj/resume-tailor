-- Tracks whether the user has actually applied with a run's tailored resume, and where.
--
-- The idea: several tailoring runs can be queued (and reviewed) before the user goes and
-- applies to any of them, so "applied" is a fact the user records after the fact, not
-- something the pipeline can infer. It has no status lifecycle of its own -- it is a plain
-- flag the user flips from the history list or a run's own page.
--
--   applied           whether the user has applied using this run's tailored resume
--   applied_at        when they flipped it on; null while applied is false
--   application_link  the posting or application-tracker URL for this run, so it can be
--                     reopened later; free text, not validated as a URL
ALTER TABLE tailoring_runs ADD COLUMN applied BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tailoring_runs ADD COLUMN applied_at TIMESTAMPTZ;
ALTER TABLE tailoring_runs ADD COLUMN application_link TEXT;
CREATE INDEX idx_tailoring_runs_owner_applied ON tailoring_runs (owner_id, applied);
