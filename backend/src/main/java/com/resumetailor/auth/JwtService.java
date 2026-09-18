package com.resumetailor.auth;

import com.resumetailor.config.JwtProperties;
import com.resumetailor.user.Role;
import com.resumetailor.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates the app's own session token. Google Sign-In only proves who the
 * user is at the moment they sign in; this token is what proves it on every request
 * afterwards, so the backend never re-verifies a Google token per call.
 *
 * <p>The signing key is validated at startup, the same way {@code ApiKeyCipher} refuses
 * to start on a missing or too-short {@code ENCRYPTION_KEY}.
 */
@Component
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final int MIN_SECRET_BYTES = 32;
    private static final int DEFAULT_EXPIRATION_MINUTES = 1440;

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.key = loadKey(properties.secret());
        this.expiration = Duration.ofMinutes(
                properties.expirationMinutes() != null ? properties.expirationMinutes() : DEFAULT_EXPIRATION_MINUTES);
    }

    private static SecretKey loadKey(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("""
                    JWT_SECRET is not set. It signs session tokens issued after Google
                    Sign-In. Generate one and put it in backend/.env:
                      openssl rand -base64 48""");
        }
        byte[] bytes = configured.trim().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes (got " + bytes.length
                            + "). Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Returns null -- never throws -- for a missing, malformed, expired or tampered token. */
    public AuthenticatedPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new AuthenticatedPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public record AuthenticatedPrincipal(UUID userId, String email, Role role) {
    }
}
