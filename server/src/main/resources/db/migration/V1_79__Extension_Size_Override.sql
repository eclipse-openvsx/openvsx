CREATE SEQUENCE IF NOT EXISTS extension_size_override_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.extension_size_override
(
    id BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('extension_size_override_seq'),
    scope_namespace_id BIGINT NOT NULL REFERENCES public.namespace(id) ON DELETE CASCADE,
    scope_extension_id BIGINT REFERENCES public.extension(id) ON DELETE CASCADE,
    max_size BIGINT NOT NULL,
    created_at TIMESTAMP without time zone NOT NULL,
    updated_at TIMESTAMP without time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS extension_size_override_scope_idx
    ON public.extension_size_override (scope_namespace_id, COALESCE(scope_extension_id, 0));
