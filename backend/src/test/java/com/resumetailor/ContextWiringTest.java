package com.resumetailor;

import com.resumetailor.export.ExportService;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.resume.ResumeService;
import com.resumetailor.tailoring.TailoringRunner;
import com.resumetailor.tailoring.TailoringService;
import com.resumetailor.user.CurrentUserProvider;
import com.resumetailor.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application context against in-memory H2 so the bean graph, async
 * configuration, controllers and startup seeding are exercised on every `mvn test` --
 * no Docker required.
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

    @Test
    void everyServiceWiresAndTheSingleUserIsSeededOnStartup() {
        assertThat(resumeService).isNotNull();
        assertThat(tailoringService).isNotNull();
        assertThat(tailoringRunner).isNotNull();
        assertThat(llmProfileService).isNotNull();
        assertThat(exportService).isNotNull();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(currentUserProvider.currentUserId()).isNotNull();
    }

    @Test
    void anEmptyLibraryReadsBackCleanlyThroughTheOwnerScopedQueries() {
        assertThat(resumeService.list()).isEmpty();
        assertThat(llmProfileService.list()).isEmpty();
    }
}
