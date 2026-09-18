package com.resumetailor;

import com.resumetailor.llm.LlmProfileRepository;
import com.resumetailor.resume.ResumeRepository;
import com.resumetailor.user.CurrentUserProvider;
import com.resumetailor.user.Role;
import com.resumetailor.user.User;
import com.resumetailor.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against a real Postgres.
 *
 * <p>This is the test that proves Flyway's schema and the JPA entities agree:
 * {@code spring.jpa.hibernate.ddl-auto=validate} fails startup on any mismatch. It needs
 * Docker, so it is tagged "integration" and excluded from {@code mvn test}. Run it with:
 *
 * <pre>mvn verify -Pintegration</pre>
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class SchemaValidationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private LlmProfileRepository llmProfileRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 32 zero bytes, base64 -- valid shape, test-only value.
        registry.add("resume-tailor.security.encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("resume-tailor.llm.default-api-key", () -> "");
        registry.add("resume-tailor.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
        registry.add("resume-tailor.jwt.secret", () -> "test-jwt-secret-at-least-32-bytes-long-for-hs256");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void schemaMatchesEntitiesIncludingTheNewAuthColumns() {
        // Reaching this point means Flyway ran V2__add_auth.sql and Hibernate validated
        // every mapping (google_sub, role, picture_url included) against it.
        User user = userRepository.save(new User(
                "owner@resume-tailor.local", "Me", "google-sub-123", "https://example.com/pic.jpg",
                Role.NORMAL_USER));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));

        UUID ownerId = currentUserProvider.currentUserId();
        assertThat(ownerId).isEqualTo(user.getId());
        assertThat(resumeRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)).isEmpty();
        assertThat(llmProfileRepository.countByOwnerId(ownerId)).isZero();
    }
}
