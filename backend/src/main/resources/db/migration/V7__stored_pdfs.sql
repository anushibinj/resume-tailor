-- PDFs kept in configurable storage (a local directory or S3), including ones the user
-- made outside the app and uploaded for a run.
--
--   file_path → storage_key  a key in the configured storage, e.g.
--                            users/{owner}/runs/{run}/compiled.pdf. Rows written before
--                            this migration hold the absolute path the PDF was written to;
--                            local storage still reads those from where they are.
--   UPLOADED_PDF             a PDF the user uploaded. Kept beside the compiled one (kind
--                            PDF) rather than replacing it, one of each per run.
--   original_filename        the uploaded file's own name, shown back to the user; null for
--                            compiled PDFs. Display only -- downloads are always named from
--                            the run, so this never reaches a response header.
--
-- Still no owner_id: an artifact is only ever reached through its run, and the run is
-- looked up owner-scoped first.
ALTER TABLE artifacts RENAME COLUMN file_path TO storage_key;

ALTER TABLE artifacts DROP CONSTRAINT artifacts_kind_check;
ALTER TABLE artifacts ADD CONSTRAINT artifacts_kind_check
    CHECK (kind IN ('PDF', 'SOURCE', 'UPLOADED_PDF'));

ALTER TABLE artifacts ADD COLUMN original_filename VARCHAR(255);
