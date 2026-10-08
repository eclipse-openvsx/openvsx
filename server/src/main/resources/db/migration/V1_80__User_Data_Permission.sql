-- Fine-grained admin permissions grantable/revocable per user, independent of role.
-- ADMIN keeps implying every permission (see UserData#hasPermission) and needs no row here.
CREATE TABLE public.user_data_permission(
    user_data_id BIGINT NOT NULL,
    permission CHARACTER VARYING(32) NOT NULL,
    PRIMARY KEY (user_data_id, permission)
);

ALTER TABLE ONLY public.user_data_permission
    ADD CONSTRAINT user_data_permission_fkey FOREIGN KEY (user_data_id) REFERENCES public.user_data(id) ON DELETE CASCADE;
