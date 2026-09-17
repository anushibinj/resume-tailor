package com.resumetailor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Tailoring runs take tens of seconds, so they execute off the request thread: the
 * controller returns a run id immediately and the frontend polls for status.
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
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
