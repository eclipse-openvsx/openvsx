-- Tier-based rate limiting moved to the deployment that uses it. Refuse to drop tables that still
-- hold rows, so a deployment that has not moved its data yet fails the migration instead of losing it.
DO $$
DECLARE
    t text;
    n bigint;
    filled text[] := '{}';
BEGIN
    FOREACH t IN ARRAY ARRAY['daily_usage_stats', 'usage_stats', 'rate_limit_token', 'customer_membership', 'customer', 'tier'] LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', t) INTO n;
        IF n > 0 THEN
            filled := filled || t;
        END IF;
    END LOOP;
    IF cardinality(filled) > 0 THEN
        RAISE EXCEPTION 'rate limit tables still hold rows: %. Export the data or delete the rows by hand before upgrading', array_to_string(filled, ', ');
    END IF;
END
$$;

DROP TABLE public.daily_usage_stats;
DROP TABLE public.usage_stats;
DROP TABLE public.rate_limit_token;
DROP TABLE public.customer_membership;
DROP TABLE public.customer;
DROP TABLE public.tier;

-- Sequences owned by a column are already gone with their table; the rest were created standalone.
DROP SEQUENCE IF EXISTS public.daily_usage_stats_seq;
DROP SEQUENCE IF EXISTS public.usage_stats_seq;
DROP SEQUENCE IF EXISTS public.rate_limit_token_seq;
DROP SEQUENCE IF EXISTS public.customer_membership_seq;
DROP SEQUENCE IF EXISTS public.customer_seq;
DROP SEQUENCE IF EXISTS public.tier_seq;
