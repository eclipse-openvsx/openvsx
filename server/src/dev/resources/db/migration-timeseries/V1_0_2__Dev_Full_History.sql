-- Development only, like V1_0_1: this file lives in src/dev/resources, so it is on the classpath of
-- a dev run and invisible to every other deployment.
--
-- The shipped policies cap analytics at 90 days twice over, which is right for production and wrong
-- for a local database filled by replaying generated log files:
--
--   * the refresh policy's start_offset bounds what it will materialize, so a day backfilled outside
--     that window is never materialized. Reads come from download_stats_daily, so the day reads as
--     zero while sitting in download_event, and no amount of waiting for the policy fixes it.
--   * the retention policy then drops those raw rows, after which the day cannot be recovered at all.
--
-- Dropping start_offset lets the policy refresh the whole history; removing retention keeps the raw
-- events. The refresh job is altered in place rather than replaced, so the one-minute schedule from
-- V1_0_1 survives - and its next run materializes the older data by itself, which is why this does
-- not force a refresh here: refresh_continuous_aggregate cannot run inside a transaction, and
-- waiting a minute is cheaper than an executeInTransaction=false sidecar.
SELECT alter_job(job_id, config => config - 'start_offset')
FROM timescaledb_information.jobs
WHERE proc_name = 'policy_refresh_continuous_aggregate';

SELECT remove_retention_policy('download_event', if_exists => true);
