-- Keyword coverage moves from a deterministic Java matcher to a judgment by the user's
-- LLM, and every gap becomes an addable suggestion linked to the requirement it covers.
--
-- Why the matcher was retired: models extract requirements as descriptive phrases
-- ("Java (Programming Language)", "application performance optimization"), and literal
-- phrase matching marked a Java developer's resume as missing Java.

-- ----------------------------------------------------------- tailoring_runs
-- Gap analysis runs after the rewrite and can fail or be re-run on its own, so it has
-- its own status. OUTDATED marks runs whose coverage came from the retired matcher: the
-- UI hides those numbers and offers a re-check instead of showing known-wrong results.
ALTER TABLE tailoring_runs ADD COLUMN gaps_status VARCHAR(16);
ALTER TABLE tailoring_runs ADD COLUMN gaps_error TEXT;
UPDATE tailoring_runs SET gaps_status = CASE WHEN status = 'COMPLETED' THEN 'OUTDATED' ELSE 'PENDING' END;
ALTER TABLE tailoring_runs ALTER COLUMN gaps_status SET NOT NULL;
ALTER TABLE tailoring_runs ALTER COLUMN gaps_status SET DEFAULT 'PENDING';
ALTER TABLE tailoring_runs ADD CONSTRAINT ck_tailoring_runs_gaps_status
    CHECK (gaps_status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'OUTDATED'));

-- ---------------------------------------------------------- run_suggestions
-- An addition is placed by anchoring to a verbatim snippet of the resume, so it lands
-- in the template's own structure (an AltaCV comma list, an itemize, a Markdown list).
-- Insert-only by construction: accepting a suggestion can never delete resume text.
--   keyword     the job requirement this addresses (null for pre-V2 suggestions)
--   anchor      exact text already in the resume to insert next to
--   placement   AFTER or BEFORE the anchor
--   insert_text the exact markup to insert; `content` stays the human-readable label
-- Rows without an anchor (all pre-V2 rows) fall back to section-based placement.
ALTER TABLE run_suggestions ADD COLUMN keyword VARCHAR(255);
ALTER TABLE run_suggestions ADD COLUMN anchor TEXT;
ALTER TABLE run_suggestions ADD COLUMN placement VARCHAR(8);
ALTER TABLE run_suggestions ADD COLUMN insert_text TEXT;
ALTER TABLE run_suggestions ADD CONSTRAINT ck_run_suggestions_placement
    CHECK (placement IS NULL OR placement IN ('AFTER', 'BEFORE'));

-- ------------------------------------------------------------- run_keywords
-- covered is the model's judgment of the tailored resume, with evidence quoted from it.
-- A requirement also counts as covered when its linked suggestion is ACCEPTED; that is
-- computed, not stored, so un-accepting reverts it without another model call.
ALTER TABLE run_keywords ADD COLUMN covered BOOLEAN;
ALTER TABLE run_keywords ADD COLUMN evidence TEXT;
ALTER TABLE run_keywords ADD COLUMN suggestion_id UUID REFERENCES run_suggestions (id) ON DELETE SET NULL;
UPDATE run_keywords SET covered = present_in_tailored;
ALTER TABLE run_keywords ALTER COLUMN covered SET NOT NULL;
ALTER TABLE run_keywords DROP COLUMN present_in_original;
ALTER TABLE run_keywords DROP COLUMN present_in_tailored;
CREATE INDEX idx_run_keywords_suggestion ON run_keywords (suggestion_id);

-- -------------------------------------------------------------- jd_analyses
-- The extraction prompt changed (no locations or parenthetical qualifiers). Cached
-- analyses record the prompt version that produced them and are redone when stale.
ALTER TABLE jd_analyses ADD COLUMN prompt_version INTEGER NOT NULL DEFAULT 1;
