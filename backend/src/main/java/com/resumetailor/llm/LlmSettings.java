package com.resumetailor.llm;

import java.math.BigDecimal;

/**
 * A profile resolved for use: the API key is decrypted here and this object is never
 * serialised to a response.
 */
public record LlmSettings(
        String baseUrl,
        String apiKey,
        String model,
        BigDecimal temperature,
        Integer maxOutputTokens) {
}
