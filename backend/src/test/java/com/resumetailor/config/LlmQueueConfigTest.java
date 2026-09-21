package com.resumetailor.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that {@code resume-tailor.llm.queue.*} actually shapes the executor: a fixed-size
 * pool at the configured concurrency, with a queue behind it of the configured capacity.
 */
class LlmQueueConfigTest {

    @Test
    void executorIsSizedFromTheConfiguredProperties() {
        Executor executor = new LlmQueueConfig(new LlmQueueProperties(2, 5)).llmExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertThat(pool.getCorePoolSize()).isEqualTo(2);
        assertThat(pool.getMaxPoolSize()).isEqualTo(2);
        assertThat(pool.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(5);
    }

    @Test
    void defaultsToOneAtATimeWhenConfiguredThatWay() {
        Executor executor = new LlmQueueConfig(new LlmQueueProperties(1, 100)).llmExecutor();

        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertThat(pool.getCorePoolSize()).isEqualTo(1);
        assertThat(pool.getMaxPoolSize()).isEqualTo(1);
    }
}
