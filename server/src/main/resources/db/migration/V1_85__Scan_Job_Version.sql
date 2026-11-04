-- Optimistic-locking version for scan_job: a write based on a stale read of a row now fails instead
-- of silently overwriting a verdict (e.g. the timeout watchdog's FAILED) that was recorded meanwhile.
--
-- A constant default is stored in the catalog, not written into the rows, so this does not rewrite
-- the table. Existing rows start at version 0.

ALTER TABLE public.scan_job
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
