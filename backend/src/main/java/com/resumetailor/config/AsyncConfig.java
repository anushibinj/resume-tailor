package com.resumetailor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

import java.util.concurrent.Executor;

/**
 * Tailoring runs take tens of seconds, so they execute off the request thread: the
 * controller returns a run id immediately and the frontend polls for status.
 *
 * <p>The signed-in user lives in a thread-local {@code SecurityContext}, which a pool
 * thread does not inherit. The task decorator carries the submitting request's context
 * onto the worker, otherwise every owner-scoped lookup in the pipeline fails with "no
 * authenticated user".
 */
@Configuration
public class AsyncConfig {

    public static final String TAILORING_EXECUTOR = "tailoringExecutor";

    @Bean(TAILORING_EXECUTOR)
    public Executor tailoringExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("tailoring-");
        // Runs on the submitting thread, so the context captured here is the request's.
        executor.setTaskDecorator(DelegatingSecurityContextRunnable::new);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
