package com.resumetailor.llm;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class LlmDtos {

    private LlmDtos() {
    }

    /**
     * @param apiKey on update, leave null/blank to keep the stored key unchanged --
     *               the current key is never sent to the browser, so the form cannot
     *               round-trip it.
     */
    public record SaveLlmProfileRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 1024) String baseUrl,
            String apiKey,
            @NotBlank @Size(max = 255) String model,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            @Min(256) @Max(200000) Integer maxOutputTokens,
            Boolean makeDefault) {
    }

    /** Note the absence of any field carrying the API key. */
    public record LlmProfileResponse(
            UUID id,
            String name,
            String baseUrl,
            String model,
            BigDecimal temperature,
            Integer maxOutputTokens,
            boolean isDefault,
            boolean hasApiKey,
            String apiKeyMask,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TestConnectionResponse(boolean ok, String message, String model, long latencyMs) {
    }
}
