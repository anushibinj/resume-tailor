package com.resumetailor.config;

import com.resumetailor.user.SecurityContextUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bug where a tailoring run failed with "No authenticated user in the security
 * context": the pipeline runs on a pool thread, which does not inherit the request's
 * thread-local SecurityContext.
 */
class AsyncConfigTest {

    private final SecurityContextUserProvider userProvider = new SecurityContextUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void workerThreadSeesTheUserWhoSubmittedTheTask() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));

        Executor executor = new AsyncConfig().tailoringExecutor();
        CompletableFuture<UUID> seenOnWorker = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                seenOnWorker.complete(userProvider.currentUserId());
            } catch (RuntimeException ex) {
                seenOnWorker.completeExceptionally(ex);
            }
        });

        assertThat(seenOnWorker.get(5, TimeUnit.SECONDS)).isEqualTo(userId);
    }
}
