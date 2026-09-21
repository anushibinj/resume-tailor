package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds how many LLM calls run at once, independent of how many tailoring runs are in
 * flight. Every call to {@code OpenAiCompatibleClient.chat()} -- tailoring, gap analysis,
 * summary options, resume Q&amp;A, the Settings "Test" button -- goes through one queue, so
 * queuing several tailoring runs in parallel cannot fire several concurrent requests at the
 * user's own LLM endpoint.
 *
 * @param concurrency how many LLM calls may run at the same time; default 1, so a queue of
 *                     runs is worked one call at a time rather than hammering the endpoint
 * @param capacity     how many calls may wait behind those before a new one is refused
 *                     rather than growing the backlog without bound
 */
@ConfigurationProperties(prefix = "resume-tailor.llm.queue")
public record LlmQueueProperties(int concurrency, int capacity) {
}
