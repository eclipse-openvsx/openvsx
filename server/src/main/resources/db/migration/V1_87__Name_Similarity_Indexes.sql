-- Trigram indexes for the NAME_SIMILARITY lookups (see NameSimilarityJooqRepository), which find the
-- active extensions whose name or display name resembles a given one with the % operator.
--
-- The display name expression has to match the one used by the query, or the planner falls back to a
-- sequential scan over every version: casing and surrounding whitespace are normalised away, the
-- trimmed set being every Unicode space separator. It is the same normalisation as the
-- DISPLAY_NAME_CONFLICT check's extension_version_display_name_idx.
--
-- Restricted to the versions and extensions that are publicly visible, which is all the lookups look
-- at, and which keeps the inactive versions that make up the bulk of the table out of the index.
--
-- Deliberately not CONCURRENTLY: Flyway holds a transaction open on a second connection for the whole
-- run, and CREATE INDEX CONCURRENTLY waits for every transaction open alongside it, Flyway's own
-- included, so it never returns. The plain build blocks writes to each table until it completes, and
-- lock_timeout makes it give up, to be retried, rather than queue every writer behind a build that is
-- itself waiting on a long-running transaction. To deploy without a write pause, create the indexes
-- CONCURRENTLY out of band first; IF NOT EXISTS then turns this migration into a no-op. A failed
-- CONCURRENTLY build leaves an invalid index behind that IF NOT EXISTS will not replace, so drop it
-- before retrying.

SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS extension_version_display_name_trgm_idx
    ON public.extension_version
    USING gin (lower(btrim(display_name, U&' \00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\202F\205F\3000')) gin_trgm_ops)
    WHERE active;

CREATE INDEX IF NOT EXISTS extension_name_trgm_idx
    ON public.extension
    USING gin (lower(name) gin_trgm_ops)
    WHERE active;
