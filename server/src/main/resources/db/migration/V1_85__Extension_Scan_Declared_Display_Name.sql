-- The display name the package declares, NULL when it declares none. extension_display_name keeps the
-- label shown in the admin UI, which falls back to the extension name; the DISPLAY_NAME_CONFLICT check
-- must not compare that fallback against other extensions' display names.
ALTER TABLE public.extension_scan ADD COLUMN declared_display_name VARCHAR(255);
