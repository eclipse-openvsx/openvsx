-- Trigram similarity (similarity(), the % operator and the gin_trgm_ops index class) behind the
-- NAME_SIMILARITY publish check and the name squatting admin dashboard. pg_trgm is a trusted
-- extension since PostgreSQL 13, so a database owner without superuser rights can create it.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SEQUENCE IF NOT EXISTS name_similarity_allow_seq START WITH 1 INCREMENT BY 1;

-- Pairs of extensions an admin has confirmed are not squatting on each other. A pair is stored once,
-- whichever way round it was allowed: extension_id is the look-alike that was allowed and
-- other_extension_id the extension it was allowed alongside. field, similarity and the two values
-- record the match as the admin saw it, to tell when either name has changed since.
CREATE TABLE IF NOT EXISTS public.name_similarity_allow
(
    id BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('name_similarity_allow_seq'),
    extension_id BIGINT NOT NULL REFERENCES public.extension(id) ON DELETE CASCADE,
    other_extension_id BIGINT NOT NULL REFERENCES public.extension(id) ON DELETE CASCADE,
    field VARCHAR(16) NOT NULL,
    similarity REAL NOT NULL,
    extension_value VARCHAR(255) NOT NULL,
    other_value VARCHAR(255) NOT NULL,
    reason VARCHAR(2048),
    decided_by_id BIGINT REFERENCES public.user_data(id) ON DELETE SET NULL,
    decided_at TIMESTAMP without time zone NOT NULL,
    CONSTRAINT name_similarity_allow_distinct_ck CHECK (extension_id <> other_extension_id),
    CONSTRAINT name_similarity_allow_field_ck CHECK (field IN ('NAME', 'DISPLAY_NAME', 'NAMESPACE')),
    CONSTRAINT name_similarity_allow_similarity_ck CHECK (similarity >= 0 AND similarity <= 1)
);

-- Without this pg_get_serial_sequence cannot find the sequence, which defeats
-- scripts/import-db-dump.sh's sequence reset after loading a dump.
ALTER SEQUENCE name_similarity_allow_seq OWNED BY public.name_similarity_allow.id;

CREATE UNIQUE INDEX IF NOT EXISTS name_similarity_allow_pair_idx
    ON public.name_similarity_allow (LEAST(extension_id, other_extension_id), GREATEST(extension_id, other_extension_id));

-- The unique index leads with LEAST(...), so it cannot serve the ON DELETE CASCADE lookups by either column.
CREATE INDEX IF NOT EXISTS name_similarity_allow_extension_idx ON public.name_similarity_allow (extension_id);
CREATE INDEX IF NOT EXISTS name_similarity_allow_other_extension_idx ON public.name_similarity_allow (other_extension_id);
CREATE INDEX IF NOT EXISTS name_similarity_allow_decided_by_idx ON public.name_similarity_allow (decided_by_id);
