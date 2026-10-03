-- Staff management: a tenant admin must be able to add the people who work there.
--
-- Two additions, both about the fact that a role belongs to a *membership*, not to a
-- person. `users` is one shared identity per human; `tenant_users` is that person's
-- relationship to one hospital. So:
--
--   1. tenant_users.active -- access is revoked per hospital. Deactivating someone at
--      one clinic must not lock them out of a second clinic they also work at, which is
--      exactly what flipping the global users.active would do. The schema already
--      supports a clinician holding a different role at each clinic; deactivation had to
--      match that or it would have been the one part of the model that ignored it.
--
--   2. users.must_change_password -- an admin sets the first password and hands it over.
--      Until its owner replaces it, that password is a shared secret held by two people,
--      so the account is flagged to force a change at first sign-in.

ALTER TABLE tenant_users
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN tenant_users.active IS
    'Whether this person may sign in to THIS hospital. Scoped per membership so '
    'deactivating at one clinic does not revoke access at another.';

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.must_change_password IS
    'True while the account still carries an admin-set password. Cleared once its owner '
    'chooses their own.';
