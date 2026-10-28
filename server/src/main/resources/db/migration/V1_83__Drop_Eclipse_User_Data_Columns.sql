-- The Eclipse login data moved to the deployment that uses it. Refuse to drop columns that still hold
-- data, so a deployment that has not moved it yet fails the migration instead of losing it.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.user_data WHERE eclipse_token IS NOT NULL OR eclipse_person_id IS NOT NULL) THEN
        RAISE EXCEPTION 'user_data.eclipse_token and user_data.eclipse_person_id still hold data: move it out of user_data before this migration';
    END IF;
END
$$;

ALTER TABLE public.user_data
    DROP COLUMN eclipse_token,
    DROP COLUMN eclipse_person_id;
