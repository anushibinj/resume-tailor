package com.resumetailor.user;

import java.util.UUID;

/**
 * The single seam between "who is asking" and the rest of the application.
 *
 * <p>{@link SecurityContextUserProvider} reads the id Spring Security's
 * {@code SecurityContext} carries for the current request -- put there by
 * {@code JwtAuthenticationFilter} once it validates the app's session token issued after
 * Google Sign-In. No schema change and no query change was needed to add real auth,
 * because every owned table already had {@code owner_id} and every service already
 * filtered by this value.
 *
 * <p>Services must never call {@code findAll()} on an owned repository. Always scope
 * to {@link #currentUserId()}.
 */
public interface CurrentUserProvider {

    UUID currentUserId();
}
