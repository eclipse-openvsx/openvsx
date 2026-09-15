-- personal_access_token.version and .type became NOT NULL in V1_72__Trusted_Publisher.sql without a
-- default, so every INSERT has to name them. The application always does - PersonalAccessToken has no
-- @DynamicInsert, so Hibernate lists every column on every insert - but hand-written SQL now has to
-- know about two columns it previously didn't, and the bootstrap procedure documented for registries
-- without a login provider (deploy/kubernetes/README.md, and the wiki) fails on a current schema.
--
-- These defaults are chosen so that an INSERT written against the pre-V1_72 schema still produces a
-- working token - the row that migration's own backfill would have left behind for an ordinary
-- long-lived one. They do not reproduce that backfill in full: it also derived type = 'OTT' from the
-- description of the one-time publish tokens the server used to create, which is a classification of
-- rows that already existed, not something a hand-written INSERT has any reason to be creating.

ALTER TABLE ONLY public.personal_access_token
    -- The oldest token format, deliberately, and it must stay pinned at 0 rather than tracking
    -- AccessTokenService.TOKEN_CURRENT_VERSION. Version 0 means "the value column still holds the raw
    -- token": the hash pepper lives in the application configuration and never reaches the database, so
    -- a value inserted by hand cannot be anything else. AccessTokenService#useAccessToken falls back to
    -- a plaintext lookup, hashes the row in place and bumps its version on first use, and the startup
    -- upgrade job does the same for tokens that are never used. Raising this default to a newer version
    -- would make such a row be read as an already-hashed value in that format, and it would silently
    -- never authenticate again - so version 0 has to keep being understood by upgradeToken for as long
    -- as this default stands.
    ALTER COLUMN version SET DEFAULT 0,
    -- The only type an operator would create by hand: a normal long-lived token. OTT and TPT are issued
    -- by the server itself, are ephemeral, and carry state (a scope, a trusted publisher registration,
    -- OIDC claims) that an INSERT omitting the type would not be supplying either.
    ALTER COLUMN type SET DEFAULT 'LLT';
