-- Every insert path sets published_by_id, so the column can be enforced. A remaining NULL row fails
-- this migration loudly instead of being given an arbitrary publisher.
--
-- Validating a NOT VALID check separately from adding it keeps the ACCESS EXCLUSIVE lock brief:
-- the table scan runs under the weaker lock VALIDATE CONSTRAINT takes, and Postgres can then use
-- the validated check to skip re-scanning the table for SET NOT NULL.
ALTER TABLE extension_version ADD CONSTRAINT extension_version_published_by_id_not_null
    CHECK (published_by_id IS NOT NULL) NOT VALID;
ALTER TABLE extension_version VALIDATE CONSTRAINT extension_version_published_by_id_not_null;
ALTER TABLE extension_version ALTER COLUMN published_by_id SET NOT NULL;
ALTER TABLE extension_version DROP CONSTRAINT extension_version_published_by_id_not_null;
