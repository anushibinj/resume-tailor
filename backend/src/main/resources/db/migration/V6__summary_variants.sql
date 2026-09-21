-- Summary length options: the rewrite's summary paragraph written again at 4 to 7 lines,
-- so the user can pick how long it should be from the run page.
--
-- Everything lives on tailoring_runs, and no table is added, because the options are only
-- ever read and replaced whole (like jd_analyses.keywords) and belong to exactly one run.
--
--   summary_original  the exact text of the summary inside tailored_body; the swap replaces
--                     precisely this, so tailored_body itself stays the model's pristine rewrite
--   summary_variants  JSON array of {"lines": 4, "text": "..."}
--   summary_lines     the length currently chosen; null means "as the model wrote it"
--
-- Generation has its own status, like the gap check: it happens after the rewrite is saved,
-- can fail without costing the rewrite, and can be retried on its own. NONE marks runs that
-- predate the feature; the UI offers to generate options for them.
ALTER TABLE tailoring_runs ADD COLUMN summary_status VARCHAR(16);
ALTER TABLE tailoring_runs ADD COLUMN summary_error TEXT;
ALTER TABLE tailoring_runs ADD COLUMN summary_original TEXT;
ALTER TABLE tailoring_runs ADD COLUMN summary_variants TEXT;
ALTER TABLE tailoring_runs ADD COLUMN summary_lines INTEGER;
UPDATE tailoring_runs SET summary_status = CASE WHEN status = 'COMPLETED' THEN 'NONE' ELSE 'PENDING' END;
ALTER TABLE tailoring_runs ALTER COLUMN summary_status SET NOT NULL;
ALTER TABLE tailoring_runs ALTER COLUMN summary_status SET DEFAULT 'PENDING';
ALTER TABLE tailoring_runs ADD CONSTRAINT ck_tailoring_runs_summary_status
    CHECK (summary_status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'NONE'));
