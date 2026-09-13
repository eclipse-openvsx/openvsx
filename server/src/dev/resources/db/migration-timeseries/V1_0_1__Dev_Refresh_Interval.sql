-- Development only. This file lives in src/dev/resources, so it is on the classpath of a dev run and
-- invisible to every other deployment, the same way the registry's dev migrations work.
--
-- The shipped policy refreshes the continuous aggregate hourly, which is right for production and
-- painful locally: downloads ingested for a day in the past land below the aggregate's watermark, and
-- until the policy next runs they are served from the materialization alone, which does not have them
-- yet. They read as zero while being present in download_event, for up to an hour.
--
-- A fresh database hides this, which is the awkward part: the watermark starts at -infinity, so
-- everything is answered by real-time aggregation and backfilled history appears at once. The gap
-- only opens after the policy first runs, so local analytics appear to work and then quietly stop
-- reflecting new history.
SELECT alter_job(job_id, schedule_interval => INTERVAL '1 minute')
FROM timescaledb_information.jobs
WHERE proc_name = 'policy_refresh_continuous_aggregate';
