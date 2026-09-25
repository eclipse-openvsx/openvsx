-- Renaming a namespace (ChangeNamespaceService) moves every extension straight to the new namespace row
-- without appending anything to the changes feed: extension_version_change.namespace is a snapshot taken
-- only when a transition is recorded, so a version that moved without one keeps reporting its stale,
-- pre-rename tuple as ACTIVE (or INACTIVE) forever, and its new tuple never appears until an unrelated
-- transition eventually touches it (#2244). This backfills the two entries a live rename now records
-- for every version whose last reported tuple no longer matches where it actually lives.
--
-- changed_at is stamped with the time this migration runs, not a reconstructed rename time - there is no
-- record of when any of these renames actually happened, and backdating it would sort the entry into a
-- part of the feed a caught-up consumer has already read past, exactly the bug being fixed here (see the
-- changedAt javadoc on ExtensionVersionChange). Captured once into the temp table so both inserts below
-- share the exact same instant, the same way a live rename reports all of its versions at one instant.
CREATE TEMPORARY TABLE tmp_namespace_rename_repair ON COMMIT DROP AS
WITH latest_change AS (
    -- The tuple last reported for each version still referenced by an entry - findLatestExtensionVersionChange
    -- in SQL. A purged version (extension_version_id already NULL) has nothing left to compare against and
    -- is left out; unlike a rename, a purge is always reported at the time it happens.
    SELECT DISTINCT ON (evc.extension_version_id)
        evc.extension_version_id,
        evc.namespace AS reported_namespace,
        evc.extension AS reported_extension,
        evc.version AS reported_version,
        evc.target_platform AS reported_target_platform,
        evc.state AS reported_state
    FROM public.extension_version_change evc
    WHERE evc.extension_version_id IS NOT NULL
    ORDER BY evc.extension_version_id, evc.changed_at DESC, evc.id DESC
)
SELECT
    ev.id AS extension_version_id,
    ev.active,
    ev.timestamp,
    lc.reported_namespace AS old_namespace,
    lc.reported_extension AS old_extension,
    lc.reported_version AS old_version,
    lc.reported_target_platform AS old_target_platform,
    n.name AS current_namespace,
    e.name AS current_extension,
    -- changed_at is stored as a UTC wall-clock value (TimeUtil#getCurrentUTC), not the session's zone -
    -- AT TIME ZONE 'UTC' converts now()'s timestamptz to that regardless of how the connection is configured.
    (now() AT TIME ZONE 'UTC') AS changed_at
FROM latest_change lc
JOIN public.extension_version ev ON ev.id = lc.extension_version_id
JOIN public.extension e ON e.id = ev.extension_id
JOIN public.namespace n ON n.id = e.namespace_id
-- wasReportedAsAvailable: a version last reported REMOVED has nothing to withdraw, moved or not.
WHERE lc.reported_state <> 'REMOVED'
  -- the only sign a rename ever touched this version: the namespace it now lives under differs from the
  -- one the feed last reported it under.
  AND lc.reported_namespace <> n.name;

-- Withdraw the stale, pre-rename tuple every affected version was last reported under. Referencing the
-- version like any other transition of a row that still exists - a rename never deletes it, unlike a
-- purge, so there is no reason to detach this entry from it the way recordPurgedExtensionVersionChange
-- does.
INSERT INTO public.extension_version_change (
        extension_version_id, namespace, extension, version, target_platform, state, timestamp, changed_at)
SELECT extension_version_id, old_namespace, old_extension, old_version, old_target_platform, 'REMOVED',
        timestamp, changed_at
FROM tmp_namespace_rename_repair;

-- Announce the still-active ones under the namespace they actually live under now. An inactive version
-- is not publicly available under the new name either, so - same as
-- ChangeNamespaceService#recordNamespaceArrival - it gets no entry here and enters the feed on whatever
-- transition changes its state next.
INSERT INTO public.extension_version_change (
        extension_version_id, namespace, extension, version, target_platform, state, timestamp, changed_at)
SELECT extension_version_id, current_namespace, current_extension, old_version, old_target_platform,
        'ACTIVE', timestamp, changed_at
FROM tmp_namespace_rename_repair
WHERE active;
