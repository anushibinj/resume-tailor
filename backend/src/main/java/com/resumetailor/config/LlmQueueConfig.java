package com.resumetailor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * The single choke point every LLM call is submitted through, separate from
 * {@link AsyncConfig#tailoringExecutor()}. Several tailoring runs can be orchestrated at
 * once, but the outbound calls those runs make to the user's own LLM endpoint are queued
 * here and worked at {@link LlmQueueProperties#concurrency()} at a time -- 1 by default, so
 * a backlog of queued runs is never a burst of concurrent requests against a model the user
 * may be rate-limited on, or self-hosting on modest hardware.
 *
 * <p>No {@code SecurityContext} propagation is needed here, unlike {@code tailoringExecutor}:
 * a chat call is a pure network request and never performs an owner-scoped lookup.
 */
@Configuration
@RequiredArgsConstructor
public class LlmQueueConfig {

    public static final String LLM_EXECUTOR = "llmExecutor";

    private final LlmQueueProperties properties;

    @Bean(LLM_EXECUTOR)
    public Executor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core == max: a fixed-size pool, so concurrency never drifts above what was
        // configured just because the queue is under pressure.
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.capacity());
        executor.setThreadNamePrefix("llm-call-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
