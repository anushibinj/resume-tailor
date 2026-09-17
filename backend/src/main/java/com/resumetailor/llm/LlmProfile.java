package com.resumetailor.llm;

import com.resumetailor.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Connection settings for one OpenAI-compatible endpoint (OpenAI, Groq, Ollama,
 * LM Studio, vLLM, ...). The API key is stored encrypted and never leaves the backend.
 */
@Entity
@Table(name = "llm_profiles")
@Getter
@Setter
@NoArgsConstructor
public class LlmProfile extends AuditedEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "api_key_encrypted", columnDefinition = "text")
    private String apiKeyEncrypted;

    /** Last 4 characters of the key, so the UI can show a recognisable mask. */
    @Column(name = "api_key_hint", length = 32)
    private String apiKeyHint;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "temperature", nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_output_tokens", nullable = false)
    private Integer maxOutputTokens;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
