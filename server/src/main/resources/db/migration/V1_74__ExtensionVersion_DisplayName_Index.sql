-- Backs the display name conflict check that publishing a new extension runs (see
-- ExtensionJooqRepository#findActiveExtensionByDisplayName): a package whose display name is already
-- carried by another publisher's extension is rejected, so every initial publication looks the
-- incoming display name up once.
--
-- The expression has to match the one used by the query, or the planner falls back to a sequential
-- scan over every version there is: the lookup normalises casing and surrounding whitespace away,
-- because neither is visible enough to tell two extensions apart when they are read side by side.
-- jOOQ renders that normalisation as lower(TRIM(BOTH FROM display_name)), which PostgreSQL
-- canonicalises to lower(btrim(display_name)) -- the expression indexed here.
--
-- Restricted to the versions that are publicly visible, which is all the check looks at, and which
-- keeps the index off the inactive versions that make up the bulk of the table.
--
-- Deliberately not CONCURRENTLY. Flyway holds a transaction open on a second connection for the
-- duration of a migration run, and CREATE INDEX CONCURRENTLY only completes once every transaction
-- that was open alongside it has finished -- so under Flyway it waits on Flyway itself and never
-- returns. Setting flyway:executeInTransaction=false lets the statement start but does not help it
-- finish.
--
-- The plain build instead takes a ShareLock on extension_version, blocking writes to it until the
-- build completes, which measured around a second per 350k active versions. At the rate extensions
-- are published that is an acceptable pause. lock_timeout bounds the worse failure mode: if a
-- long-running transaction already holds a conflicting lock on the table, this gives up rather than
-- parking every subsequent writer behind a build that is itself queued, and can be retried when the
-- table is quiet.
--
-- To deploy with no write pause at all, create the index out of band first, where nothing else holds
-- a transaction open:
--     CREATE INDEX CONCURRENTLY extension_version_display_name_idx
--         ON public.extension_version (lower(btrim(display_name))) WHERE active;
-- IF NOT EXISTS then reduces this migration to a no-op. Note that a failed CONCURRENTLY build leaves
-- an invalid index behind which IF NOT EXISTS will not replace; drop it before retrying.

SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS extension_version_display_name_idx
    ON public.extension_version (lower(btrim(display_name)))
    WHERE active;
