-- How hard a reasoning model (OpenAI o-series / gpt-5...) may think before answering.
-- Hidden reasoning tokens are billed and waited for like output: gpt-5-nano spent ~5k of
-- ~6.5k completion tokens thinking before a 24-item JSON list, 40 s per call.
--
--   none     the field is not sent (non-reasoning models and local servers that reject it)
--   minimal / low / medium / high   sent as reasoning_effort
--
-- Existing profiles get 'low'; the extraction and judgment calls here do not need more.
ALTER TABLE llm_profiles ADD COLUMN reasoning_effort VARCHAR(16) NOT NULL DEFAULT 'low';
ALTER TABLE llm_profiles ADD CONSTRAINT llm_profiles_reasoning_effort_check
    CHECK (reasoning_effort IN ('none', 'minimal', 'low', 'medium', 'high'));
