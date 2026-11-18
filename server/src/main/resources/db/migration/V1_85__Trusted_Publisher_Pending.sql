-- A registration for a repository the provider does not show without authentication (a private one) is
-- stored pending, without the provider assigned IDs. It is bound to the IDs of the first verified OIDC
-- token that presents its registration code before it expires.
--   registration_code_hash: SHA-256 of the single-use code, NULL once the registration is bound
--   registration_code_expiry: until when the code can be presented, NULL once the registration is bound
ALTER TABLE public.trusted_publisher
    ADD COLUMN IF NOT EXISTS registration_code_hash CHARACTER VARYING(64),
    ADD COLUMN IF NOT EXISTS registration_code_expiry TIMESTAMP without time zone;
