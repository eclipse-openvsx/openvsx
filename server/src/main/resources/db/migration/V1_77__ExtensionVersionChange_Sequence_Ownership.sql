-- extension_version_change_seq (V1_71) was created without OWNED BY, unlike every other entity's
-- sequence (V1_40 retroactively added this same link for the sequences that predated it). Without
-- it, pg_get_serial_sequence('extension_version_change', 'id') returns NULL, silently defeating
-- anything that relies on it to find the table's sequence - concretely,
-- scripts/import-db-dump.sh's generic per-table sequence reset after loading a dump skips this
-- table entirely, so the next feed write after an import collides with an imported id.
ALTER SEQUENCE extension_version_change_seq OWNED BY public.extension_version_change.id;
