-- Schema behind the namespaces view of the name squatting admin dashboard: pairs of namespaces whose
-- names resemble each other (see NamespaceSimilarityJooqRepository), and the pairs an admin allowed.
-- pg_trgm (V1_86) and fuzzystrmatch (V1_58) are already in place.
--
-- Adding the foreign keys and building the index block writes to namespace. The table is small (tens of
-- thousands of rows), so the pause is short; lock_timeout makes the migration give up, to be retried,
-- rather than queue every writer behind it.
SET LOCAL lock_timeout = '5s';

-- name_similarity_allow (V1_86) also holds allowed pairs of namespaces: field 'NAMESPACE' marks them,
-- and they fill namespace_id and other_namespace_id where pairs of extensions fill the extension ids.
-- lookalike_value holds the name of whichever kind the look-alike is.
ALTER TABLE public.name_similarity_allow RENAME COLUMN extension_value TO lookalike_value;

ALTER TABLE public.name_similarity_allow
    ALTER COLUMN extension_id DROP NOT NULL,
    ALTER COLUMN other_extension_id DROP NOT NULL,
    ADD COLUMN namespace_id BIGINT REFERENCES public.namespace(id) ON DELETE CASCADE,
    ADD COLUMN other_namespace_id BIGINT REFERENCES public.namespace(id) ON DELETE CASCADE,
    ADD CONSTRAINT name_similarity_allow_namespace_distinct_ck CHECK (namespace_id <> other_namespace_id),
    ADD CONSTRAINT name_similarity_allow_kind_ck CHECK (
        CASE WHEN field = 'NAMESPACE'
            THEN namespace_id IS NOT NULL AND other_namespace_id IS NOT NULL
                AND extension_id IS NULL AND other_extension_id IS NULL
            ELSE extension_id IS NOT NULL AND other_extension_id IS NOT NULL
                AND namespace_id IS NULL AND other_namespace_id IS NULL
        END
    );

-- Rows of the other kind have NULL here, which a unique index never counts as a duplicate.
CREATE UNIQUE INDEX IF NOT EXISTS name_similarity_allow_namespace_pair_idx
    ON public.name_similarity_allow (LEAST(namespace_id, other_namespace_id), GREATEST(namespace_id, other_namespace_id));

-- The unique index leads with LEAST(...), so it cannot serve the ON DELETE CASCADE lookups by either column.
CREATE INDEX IF NOT EXISTS name_similarity_allow_namespace_idx ON public.name_similarity_allow (namespace_id);
CREATE INDEX IF NOT EXISTS name_similarity_allow_other_namespace_idx ON public.name_similarity_allow (other_namespace_id);

-- Finds the namespaces whose name resembles a given one with the % operator.
CREATE INDEX IF NOT EXISTS namespace_name_trgm_idx
    ON public.namespace
    USING gin (lower(name) gin_trgm_ops);
