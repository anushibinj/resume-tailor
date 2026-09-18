package com.resumetailor;

import com.resumetailor.export.ExportService;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.resume.ResumeService;
import com.resumetailor.tailoring.TailoringRunner;
import com.resumetailor.tailoring.TailoringService;
import com.resumetailor.user.CurrentUserProvider;
import com.resumetailor.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the full application context against in-memory H2 so the bean graph, async
 * configuration, controllers and security filter chain are exercised on every
 * `mvn test` -- no Docker required.
 *
 * <p>This is deliberately NOT a schema check: Flyway is off here and Hibernate generates
 * the tables from the entities. Proving that the Flyway migration and the entities agree
 * needs real Postgres and lives in {@code SchemaValidationIT} (`mvn verify -Pintegration`).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:wiring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "resume-tailor.security.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "resume-tailor.llm.default-api-key=",
        "resume-tailor.google.client-id=test-client-id.apps.googleusercontent.com",
        "resume-tailor.jwt.secret=test-jwt-secret-at-least-32-bytes-long-for-hs256",
})
class ContextWiringTest {

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private TailoringService tailoringService;

    @Autowired
    private TailoringRunner tailoringRunner;

    @Autowired
    private LlmProfileService llmProfileService;

    @Autowired
    private ExportService exportService;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyServiceWiresAndNoUserIsSeededWithoutARealSignIn() {
        assertThat(resumeService).isNotNull();
        assertThat(tailoringService).isNotNull();
        assertThat(tailoringRunner).isNotNull();
        assertThat(llmProfileService).isNotNull();
        assertThat(exportService).isNotNull();

        // v1 seeded a single local user on startup; v2 users only exist once someone
        // actually signs in with Google, so a fresh boot has none.
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void currentUserProviderReadsWhoeverTheSecurityFilterChainAuthenticated() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        assertThat(currentUserProvider.currentUserId()).isEqualTo(userId);
    }

    @Test
    void currentUserProviderRefusesToGuessWithoutAnAuthenticatedRequest() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(currentUserProvider::currentUserId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEmptyLibraryReadsBackCleanlyThroughTheOwnerScopedQueries() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        assertThat(resumeService.list()).isEmpty();
        assertThat(llmProfileService.list()).isEmpty();
    }
}
