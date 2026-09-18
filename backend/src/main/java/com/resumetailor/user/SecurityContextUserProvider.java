package com.resumetailor.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * v2 implementation of the seam described on {@link CurrentUserProvider}: the signed-in
 * user's id is whatever {@code JwtAuthenticationFilter} put in the security context for
 * this request, after validating the app's own session token.
 *
 * <p>Every endpoint that reaches a service sits behind {@code SecurityConfig}'s
 * {@code anyRequest().authenticated()}, so an unauthenticated call here means the filter
 * chain itself is misconfigured -- it should never happen in a real request.
 */
@Component
public class SecurityContextUserProvider implements CurrentUserProvider {

    @Override
    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new IllegalStateException(
                    "No authenticated user in the security context. This endpoint should be behind auth.");
        }
        return userId;
    }
}
