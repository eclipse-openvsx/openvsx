-- Time-series download analytics schema, applied to the separate timeseries database. Requires
-- a PostgreSQL image with the timescaledb extension available. Every migration in this set that
-- creates a hypertable, a continuous aggregate or one of their policies needs an
-- executeInTransaction=false sidecar, as those cannot run inside a transaction block.

CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE download_event (
    time TIMESTAMPTZ NOT NULL,
    extension_id BIGINT NOT NULL,
    extension_version_id BIGINT NOT NULL,
    namespace VARCHAR(255) NOT NULL,
    extension_name VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    target_platform VARCHAR(255) NOT NULL,
    country CHAR(2),
    ip VARCHAR(45),
    user_agent TEXT,
    count INTEGER NOT NULL DEFAULT 1
);

SELECT create_hypertable('download_event', by_range('time', INTERVAL '7 days'));

CREATE INDEX de_ext_time ON download_event (extension_id, time DESC);

-- materialized_only = false keeps the not-yet-materialized tail (e.g. today) queryable
-- through real-time aggregation, which the settling-margin logic in the query service
-- relies on.
CREATE MATERIALIZED VIEW download_stats_daily
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
       extension_id, extension_version_id, version, target_platform, country,
       SUM(count) AS downloads
FROM download_event
GROUP BY time_bucket('1 day', time), extension_id, extension_version_id, version, target_platform, country
WITH NO DATA;

-- Materialize what is already there before the policy takes over. Until its first run the
-- watermark sits at -infinity and real-time aggregation answers everything, so the gap only
-- opens once the policy advances it: from then on buckets below the watermark are served from
-- the materialization alone, and anything never materialized reads as zero.
CALL refresh_continuous_aggregate('download_stats_daily', NULL, NULL);

-- start_offset tracks the raw retention below rather than the schedule. Log ingestion applies no
-- date filter, so a delayed or backfilled file writes events well outside a short window, and
-- once the watermark has passed them they would never materialize while the raw rows are dropped
-- at 90 days. A refresh only reprocesses invalidated ranges, so the wider window costs nothing
-- when nothing old changed.
SELECT add_continuous_aggregate_policy('download_stats_daily',
    start_offset => INTERVAL '90 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

-- compress raw chunks after 7 days, drop them after 90 days; the daily aggregate is kept forever
ALTER TABLE download_event SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'extension_id',
    timescaledb.compress_orderby = 'time DESC'
);

SELECT add_compression_policy('download_event', INTERVAL '7 days');

SELECT add_retention_policy('download_event', INTERVAL '90 days');
