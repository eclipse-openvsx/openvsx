-- publisher_agreement_event: what happened to the publisher agreement of a user, whichever provider keeps it

CREATE SEQUENCE IF NOT EXISTS publisher_agreement_event_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.publisher_agreement_event
(
    id BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('publisher_agreement_event_seq'),
    -- the history goes with the user
    user_data_id BIGINT NOT NULL REFERENCES public.user_data(id) ON DELETE CASCADE,
    -- SIGNED, REVOKED or REINSTATED; the latest of the last two decides whether the user is blocked from signing
    type VARCHAR(32) NOT NULL,
    -- the version signed, for SIGNED
    version VARCHAR(255),
    event_timestamp TIMESTAMP without time zone NOT NULL,
    -- the admin may be deleted later, the record of what they did must remain
    actor_id BIGINT REFERENCES public.user_data(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS publisher_agreement_event__user_data_id__idx
    ON public.publisher_agreement_event (user_data_id, id);
