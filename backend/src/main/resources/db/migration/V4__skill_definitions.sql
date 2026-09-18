-- Plain-language explanations of the skills and requirements job postings ask for, so the
-- UI can tell a candidate what "cloud-native design patterns" actually is.
--
-- This table is deliberately NOT owner-scoped, unlike every other table here: a definition
-- is general knowledge about a term, not something a user wrote or owns. Written once (by
-- whichever user's model met the term first), it is reused for everyone whose posting asks
-- for the same thing, which saves a model's tokens and keeps the wording consistent.
-- Nothing about a candidate, their resume or the posting is ever stored here.
--
--   name_key    the requirement normalised for matching (lower-cased, parentheticals
--               dropped -- see KeywordNormalizer), so "Java (Programming Language)" and
--               "java" share one row
--   name        the wording it was first met in, for display and debugging
--   model_used  which model wrote the description, in case one turns out to be unreliable
CREATE TABLE skill_definitions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_key     VARCHAR(255) NOT NULL UNIQUE,
    name         VARCHAR(255) NOT NULL,
    description  TEXT         NOT NULL,
    model_used   VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
