-- Every insert path sets published_by_id, so the column can be enforced. A remaining NULL row fails
-- this migration loudly instead of being given an arbitrary publisher.
ALTER TABLE extension_version ALTER COLUMN published_by_id SET NOT NULL;
