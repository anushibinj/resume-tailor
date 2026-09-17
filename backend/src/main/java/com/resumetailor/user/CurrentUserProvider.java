package com.resumetailor.user;

import java.util.UUID;

/**
 * The single seam between "who is asking" and the rest of the application.
 *
 * <p>v1 ships exactly one implementation, {@link SingleUserProvider}, which always
 * returns the seeded local user. v2 adds authentication by replacing it with an
 * implementation that reads Spring Security's {@code SecurityContext} -- no schema
 * change and no query change, because every owned table already has {@code owner_id}
 * and every service already filters by this value.
 *
 * <p>Services must never call {@code findAll()} on an owned repository. Always scope
 * to {@link #currentUserId()}.
 */
public interface CurrentUserProvider {

    UUID currentUserId();
}
