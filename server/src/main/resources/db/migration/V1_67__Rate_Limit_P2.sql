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
