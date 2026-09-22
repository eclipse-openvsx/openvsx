-- Tracks which access-log files have already been backfilled into download analytics, so a
-- re-uploaded or retried file can be rejected instead of writing a second set of download_event
-- rows. Deliberately its own table rather than a download_ingestion entry: that table is the
-- registry's own log-ingestion ledger, which backfill is documented not to touch.

CREATE TABLE public.download_backfill (
    id BIGINT NOT NULL,
    file_name CHARACTER VARYING(255) NOT NULL,
    storage_type CHARACTER VARYING(32) NOT NULL,
    processed_on TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

ALTER TABLE ONLY public.download_backfill
    ADD CONSTRAINT download_backfill_pkey PRIMARY KEY (id);

-- enforced in the database too, so a concurrent retry cannot double-count under a race the
-- application-level check alone would lose
CREATE UNIQUE INDEX download_backfill_storage_type_file_name ON public.download_backfill (storage_type, file_name);

CREATE SEQUENCE IF NOT EXISTS download_backfill_seq INCREMENT 50 OWNED BY public.download_backfill.id;
