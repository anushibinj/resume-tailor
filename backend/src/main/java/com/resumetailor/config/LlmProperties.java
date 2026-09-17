package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Seed values for the "Default" LLM profile created on first start, plus the HTTP
 * timeout applied to every LLM call. Profiles themselves live in the database and are
 * edited from the Settings screen.
 */
@ConfigurationProperties(prefix = "resume-tailor.llm")
public record LlmProperties(
        String defaultBaseUrl,
        String defaultApiKey,
        String defaultModel,
        BigDecimal defaultTemperature,
        Integer defaultMaxOutputTokens,
        Integer requestTimeoutSeconds) {
}
