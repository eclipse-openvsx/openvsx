-- database changes for Rate Limit phase 2

-- customer_membership table

CREATE SEQUENCE IF NOT EXISTS customer_membership_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.customer_membership
(
    id BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('customer_membership_seq'),
    customer bigint,
    user_data bigint,

    -- foreign keys

    CONSTRAINT customer_membership_customer_fk FOREIGN KEY (customer)
        REFERENCES public.customer(id) ON DELETE CASCADE,

    CONSTRAINT customer_membership_user_data_fk FOREIGN KEY (user_data)
        REFERENCES public.user_data(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS customer_membership_namespace_idx ON public.customer_membership(customer);
CREATE INDEX IF NOT EXISTS customer_membership_user_data_idx ON public.customer_membership(user_data);

-- rate_limit_token table

CREATE SEQUENCE IF NOT EXISTS rate_limit_token_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.rate_limit_token
(
    id BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('rate_limit_token_seq'),
    customer BIGINT,
    active BOOLEAN NOT NULL,
    created_timestamp TIMESTAMP without time zone,
    description CHARACTER VARYING(2048),
    value CHARACTER VARYING(64),

    -- foreign keys

    CONSTRAINT rate_limit_token_customer_fk FOREIGN KEY (customer)
        REFERENCES public.customer(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS rate_limit_token_customer_idx ON public.rate_limit_token(customer);
CREATE INDEX IF NOT EXISTS rate_limit_token_value_idx ON public.rate_limit_token(value);

-- alter usage_stats table

ALTER TABLE ONLY public.usage_stats
    DROP CONSTRAINT usage_stats_customer_id_fk;

ALTER TABLE ONLY public.usage_stats
    ADD CONSTRAINT usage_stats_customer_id_fk FOREIGN KEY (customer_id)
        REFERENCES public.customer(id) ON DELETE CASCADE;
