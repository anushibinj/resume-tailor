package com.resumetailor.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * v1 implementation: every request is attributed to the one seeded local user.
 * Delete this class in v2 and provide a SecurityContext-backed bean instead.
 */
@Component
@RequiredArgsConstructor
public class SingleUserProvider implements CurrentUserProvider {

    private final UserService userService;

    /** The id never changes at runtime, so it is resolved once and cached. */
    private volatile UUID cachedUserId;

    @Override
    public UUID currentUserId() {
        UUID id = cachedUserId;
        if (id == null) {
            synchronized (this) {
                id = cachedUserId;
                if (id == null) {
                    id = userService.ensureDefaultUser().getId();
                    cachedUserId = id;
                }
            }
        }
        return id;
    }
}
