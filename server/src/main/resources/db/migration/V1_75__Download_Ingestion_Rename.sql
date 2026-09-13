-- The entity behind this table was renamed DownloadCountProcessedItem -> DownloadIngestion when the
-- log ingestion pipeline was reworked. The table kept its old name at that point so the rework
-- carried no schema change; this migration completes the rename so that the schema and the code
-- agree, rather than leaving a name that only makes sense against a class that no longer exists.
--
-- Everything here is a rename of an existing object: no data is copied, no column changes type, and
-- the table is not rewritten, so this is a catalog-only operation that holds an ACCESS EXCLUSIVE lock
-- for the instant it takes to update the catalog rows.
--
-- What this is NOT safe against: an older server instance still running against the renamed schema.
-- Its queries name download_count_processed_item and will fail until it is replaced. The failure is
-- loud and confined to the hourly ingestion job - this table is a ledger of which log files have been
-- processed, so a failed run neither loses nor double-counts anything, it retries on the next tick.
-- Deployments that run more than one server instance should still expect errors in that window.

ALTER TABLE public.download_count_processed_item
    RENAME TO download_ingestion;

-- Renamed separately: renaming a table leaves its indexes, constraints and sequence under their
-- original names, and a schema where those still say download_count_processed_item is exactly the
-- half-renamed state this migration exists to avoid.
ALTER TABLE public.download_ingestion
    RENAME CONSTRAINT download_count_processed_item_pkey TO download_ingestion_pkey;

ALTER INDEX public.download_count_processed_item_storage_type RENAME TO download_ingestion_storage_type;
ALTER INDEX public.download_count_processed_item_name RENAME TO download_ingestion_name;

-- The sequence keeps its OWNED BY link across the rename, so it still follows the table.
ALTER SEQUENCE public.download_count_processed_item_seq RENAME TO download_ingestion_seq;
