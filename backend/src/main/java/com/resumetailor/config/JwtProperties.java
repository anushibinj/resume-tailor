package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret            signs the app's own session tokens issued after a Google
 *                          sign-in is verified. Required, at least 32 bytes -- HS256's
 *                          minimum key size; {@code JwtService} refuses to start without it.
 * @param expirationMinutes how long an issued token is valid before the user has to sign
 *                          in with Google again.
 */
@ConfigurationProperties(prefix = "resume-tailor.jwt")
public record JwtProperties(String secret, Integer expirationMinutes) {
}
