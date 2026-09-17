package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param encryptionKey base64-encoded 32 bytes used for AES-256-GCM encryption of stored
 *                      LLM API keys. Required; the backend refuses to start without it.
 */
@ConfigurationProperties(prefix = "resume-tailor.security")
public record SecurityProperties(String encryptionKey) {
}
