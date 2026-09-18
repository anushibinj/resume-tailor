-- v2: real authentication via Google Sign-In, plus RBAC.
--
-- google_sub is the stable, unique identifier Google assigns per account (the ID
-- token's "sub" claim) -- it is what a signed-in user is looked up by, not email. It is
-- nullable to keep any pre-existing v1 rows valid; SingleUserProvider is gone, so
-- every new row is created with one going forward.
--
-- role drives RBAC. Every new user is created NORMAL_USER (see UserService); ORG_ADMIN
-- and ADMIN exist as an enum today so promoting a user later is a data change, not a
-- schema one.
ALTER TABLE users
    ADD COLUMN google_sub  VARCHAR(255) UNIQUE,
    ADD COLUMN role        VARCHAR(32)  NOT NULL DEFAULT 'NORMAL_USER',
    ADD COLUMN picture_url VARCHAR(1024);
