-- Not CONCURRENTLY, which never returns inside Flyway's transaction; doc/deployment.md builds them
-- ahead of time. The display name expression must match NameSimilarityJooqRepository's, or the
-- lookups scan every version.

SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS extension_version_display_name_trgm_idx
    ON public.extension_version
    USING gin (lower(btrim(display_name, U&' \00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\202F\205F\3000')) gin_trgm_ops)
    WHERE active;

CREATE INDEX IF NOT EXISTS extension_name_trgm_idx
    ON public.extension
    USING gin (lower(name) gin_trgm_ops)
    WHERE active;

-- scan_id makes the walk over a check type's failures index-only and orders ties to resume from.
CREATE INDEX IF NOT EXISTS extension_validation_failure_type_detected_idx
    ON public.extension_validation_failure (validation_type, detected_at DESC, scan_id DESC);
