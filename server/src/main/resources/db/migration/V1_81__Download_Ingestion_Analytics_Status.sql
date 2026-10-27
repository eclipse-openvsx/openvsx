-- A log file used to be deleted once its counters were committed, whether or not its analytics
-- events could be stored, so an outage of the analytics database lost them for good. The status
-- keeps the file until they are stored: PENDING from the registry commit until the analytics write
-- succeeds, DONE afterwards, NONE when there was nothing to store (analytics off, or no downloads).
--
-- A constant default is stored in the catalog, not written into the rows, so this does not rewrite
-- the table. Existing rows become NONE, which is what they were: nothing is pending for them.

ALTER TABLE public.download_ingestion
    ADD COLUMN analytics_status character varying(16) NOT NULL DEFAULT 'NONE';
