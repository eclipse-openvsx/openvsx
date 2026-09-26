-- A namespace rename used to move extensions without appending anything to the changes feed, so a
-- version's pre-rename tuple stayed reported ACTIVE/INACTIVE forever and its new tuple never appeared
-- (#2244). Backfills the REMOVED/ACTIVE pair a live rename now records, for every version whose reported
-- history doesn't match where it lives now.
--
-- changed_at is this migration's own run time, not a reconstructed (unknown) rename time - backdating it
-- would sort the entry into a part of the feed a caught-up consumer already read past (see the changedAt
-- javadoc on ExtensionVersionChange). Captured once per temp table so departures and arrivals agree on it.
--
-- Known limitation: a version renamed back to a namespace it occupied before (A -> B -> A) is only fixed
-- correctly if A's own last entry already agrees with the version's current state - this only ever closes
-- namespaces a version has left, never corrects the one it currently occupies. Not a concern for the four
-- renames #2244 was filed about; left as a follow-up.
--
-- Locked first: a rolling deployment can have an old instance still writing while this runs. Without the
-- lock, a concurrent purge could delete a row already captured below, failing the insert on its FK; a
-- concurrent rename landing between the two snapshots would leave them looking at different namespace
-- states. SHARE mode blocks writers, not readers - same pattern as V1_76__Unique_Active_Review.sql.
LOCK TABLE public.extension, public.extension_version, public.extension_version_change IN SHARE MODE;

-- Every namespace a version has ever been reported under, and its own latest state there - kept per
-- (version, namespace) so a later, correctly-recorded transition under the new namespace (e.g. an admin
-- deactivation) can't hide an older, still-open abandoned namespace that also needs closing.
CREATE TEMPORARY TABLE tmp_namespace_rename_departures ON COMMIT DROP AS
SELECT
    nh.extension_version_id,
    nh.reported_namespace AS old_namespace,
    nh.reported_extension AS old_extension,
    nh.reported_version AS old_version,
    nh.reported_target_platform AS old_target_platform,
    ev.timestamp,
    -- UTC wall-clock, matching TimeUtil#getCurrentUTC, regardless of the connection's session timezone.
    (now() AT TIME ZONE 'UTC') AS changed_at
FROM (
    SELECT DISTINCT ON (evc.extension_version_id, evc.namespace)
        evc.extension_version_id,
        evc.namespace AS reported_namespace,
        evc.extension AS reported_extension,
        evc.version AS reported_version,
        evc.target_platform AS reported_target_platform,
        evc.state AS reported_state
    FROM public.extension_version_change evc
    WHERE evc.extension_version_id IS NOT NULL
    ORDER BY evc.extension_version_id, evc.namespace, evc.changed_at DESC, evc.id DESC
) nh
JOIN public.extension_version ev ON ev.id = nh.extension_version_id
JOIN public.extension e ON e.id = ev.extension_id
JOIN public.namespace n ON n.id = e.namespace_id
-- not already closed, and not the namespace the version actually lives under now.
WHERE nh.reported_state <> 'REMOVED'
  AND nh.reported_namespace <> n.name;

-- Still-active versions whose reported history hasn't caught up to their current namespace at all.
CREATE TEMPORARY TABLE tmp_namespace_rename_arrivals ON COMMIT DROP AS
SELECT
    ev.id AS extension_version_id,
    n.name AS current_namespace,
    e.name AS current_extension,
    ev.version,
    ev.target_platform,
    ev.timestamp,
    (now() AT TIME ZONE 'UTC') AS changed_at
FROM (
    SELECT DISTINCT ON (evc.extension_version_id)
        evc.extension_version_id,
        evc.namespace AS latest_namespace
    FROM public.extension_version_change evc
    WHERE evc.extension_version_id IS NOT NULL
    ORDER BY evc.extension_version_id, evc.changed_at DESC, evc.id DESC
) lo
JOIN public.extension_version ev ON ev.id = lo.extension_version_id
JOIN public.extension e ON e.id = ev.extension_id
JOIN public.namespace n ON n.id = e.namespace_id
WHERE ev.active
  AND lo.latest_namespace <> n.name;

-- Withdraw every abandoned tuple. References the version like any other transition of a row that still
-- exists - unlike a purge, a rename never deletes it.
INSERT INTO public.extension_version_change (
        extension_version_id, namespace, extension, version, target_platform, state, timestamp, changed_at)
SELECT extension_version_id, old_namespace, old_extension, old_version, old_target_platform, 'REMOVED',
        timestamp, changed_at
FROM tmp_namespace_rename_departures;

-- Announce the still-active ones under their current namespace.
INSERT INTO public.extension_version_change (
        extension_version_id, namespace, extension, version, target_platform, state, timestamp, changed_at)
SELECT extension_version_id, current_namespace, current_extension, version, target_platform, 'ACTIVE',
        timestamp, changed_at
FROM tmp_namespace_rename_arrivals;
