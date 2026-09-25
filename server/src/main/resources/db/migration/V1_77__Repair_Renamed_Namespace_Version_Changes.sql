-- Renaming a namespace (ChangeNamespaceService) moves every extension straight to the new namespace row
-- without appending anything to the changes feed: extension_version_change.namespace is a snapshot taken
-- only when a transition is recorded, so a version that moved without one keeps reporting its stale,
-- pre-rename tuple as ACTIVE (or INACTIVE) forever, and its new tuple never appears until an unrelated
-- transition eventually touches it (#2244). This backfills the two entries a live rename now records
-- for every version whose reported history no longer matches where it actually lives.
--
-- changed_at is stamped with the time this migration runs, not a reconstructed rename time - there is no
-- record of when any of these renames actually happened, and backdating it would sort the entry into a
-- part of the feed a caught-up consumer has already read past, exactly the bug being fixed here (see the
-- changedAt javadoc on ExtensionVersionChange). Captured once into each temp table so every row it
-- produces shares the exact same instant, the same way a live rename reports all of its versions at one
-- instant.
--
-- Known limitation: a version renamed back to a namespace it occupied before (A -> B -> A) is only
-- repaired correctly if A's own last entry already agrees with the version's current active/inactive
-- state. If it does not - e.g. A was ACTIVE, the version silently moved to B and was deactivated there,
-- then silently moved back to A - the departure logic above closes B (the abandoned tuple) but leaves A's
-- stale ACTIVE entry in place, since A is the current namespace and this migration only ever closes
-- namespaces the version has left, never corrects the one it currently occupies. Not a concern for the
-- four renames #2244 was filed about (none of them cycle back to a prior name); left as a follow-up rather
-- than folded into this fix.
--
-- Lock first, before the first snapshot below: a rolling deployment can have an old-version instance
-- still serving traffic while this migration runs on a new one. Without the lock, a purge landing between
-- the departures snapshot and its insert would delete the extension_version row that snapshot already
-- captured the id of, and the insert referencing it would violate the foreign key; a rename landing
-- between the departures and arrivals snapshots would let one see the version under its old namespace and
-- the other under a namespace newer still, repairing neither correctly. SHARE mode blocks concurrent
-- writers (INSERT/UPDATE/DELETE all take ROW EXCLUSIVE, which conflicts with SHARE) without blocking
-- readers, and Flyway runs this whole script in one transaction, so the lock is held through both
-- snapshots and both inserts. Same pattern as V1_76__Unique_Active_Review.sql.
LOCK TABLE public.extension, public.extension_version, public.extension_version_change IN SHARE MODE;

-- Every namespace a still-existing version (extension_version_id IS NOT NULL, i.e. not yet purged) has
-- ever been reported under, and the most recent state recorded specifically under that namespace. Kept
-- per (version, namespace) rather than collapsed to one row per version: a version can pick up an
-- unrelated, correctly-recorded transition under its new namespace after a silent rename (e.g. an admin
-- deactivation, which always reads the version's current, already-renamed namespace) without that ever
-- withdrawing the stale tuple its rename abandoned. Looking only at each version's single latest entry
-- would miss that abandoned tuple entirely, since the latest entry already matches the current namespace.
CREATE TEMPORARY TABLE tmp_namespace_rename_departures ON COMMIT DROP AS
SELECT
    nh.extension_version_id,
    nh.reported_namespace AS old_namespace,
    nh.reported_extension AS old_extension,
    nh.reported_version AS old_version,
    nh.reported_target_platform AS old_target_platform,
    ev.timestamp,
    -- changed_at is stored as a UTC wall-clock value (TimeUtil#getCurrentUTC), not the session's zone -
    -- AT TIME ZONE 'UTC' converts now()'s timestamptz to that regardless of how the connection is configured.
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
-- wasReportedAsAvailable: a namespace last reported REMOVED under has nothing left to withdraw.
WHERE nh.reported_state <> 'REMOVED'
  -- the namespace the version currently lives under is not an abandoned tuple - only the others are.
  AND nh.reported_namespace <> n.name;

-- The still-active versions whose reported history has not caught up to their current namespace at all,
-- i.e. their single latest entry (regardless of which namespace it was filed under) still names an old
-- one - the fully general form of "this rename was never reported", covering the version's actual current
-- location rather than any one specific abandoned tuple.
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

-- Withdraw every abandoned tuple found above. Referencing the version like any other transition of a row
-- that still exists - a rename never deletes it, unlike a purge, so there is no reason to detach this
-- entry from it the way recordPurgedExtensionVersionChange does.
INSERT INTO public.extension_version_change (
        extension_version_id, namespace, extension, version, target_platform, state, timestamp, changed_at)
SELECT extension_version_id, old_namespace, old_extension, old_version, old_target_platform, 'REMOVED',
        timestamp, changed_at
FROM tmp_namespace_rename_departures;

-- Announce the still-active ones under the namespace they actually live under now. An inactive version
-- is not publicly available under the new name either, so - same as
-- ChangeNamespaceService#recordNamespaceArrival - it gets no entry here and enters the feed on whatever
-- transition changes its state next.
INSERT INTO public.extension_version_change (
        extension_version_id, namespace, extension, version, target_platform, state, timestamp, changed_at)
SELECT extension_version_id, current_namespace, current_extension, version, target_platform, 'ACTIVE',
        timestamp, changed_at
FROM tmp_namespace_rename_arrivals;
