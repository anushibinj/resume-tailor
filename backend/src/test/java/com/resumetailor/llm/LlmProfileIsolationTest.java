package com.resumetailor.llm;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.llm.LlmDtos.LlmProfileResponse;
import com.resumetailor.llm.LlmDtos.SaveLlmProfileRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * An LLM profile -- and the API key inside it -- belongs to the user who saved it. No other
 * user can read it, change it, borrow it for a run or a question, or spend its key.
 *
 * <p>Runs against the real service and repository on in-memory H2 (same context settings as
 * {@code ContextWiringTest}, so the Spring context is shared), because the promise being
 * pinned is that the owner filter reaches the query, not merely that a method was called.
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
class LlmProfileIsolationTest {

    private static final String ALICE_KEY = "sk-alice-secret-1234";

    @Autowired
    private LlmProfileService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private UUID alicesProfileId;

    @BeforeEach
    void aliceSavesAProfile() {
        signInAs(alice);
        alicesProfileId = service.create(save("Alice's GPT", ALICE_KEY)).id();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anotherUserDoesNotSeeTheProfileInTheirList() {
        signInAs(bob);

        assertThat(service.list()).isEmpty();
    }

    @Test
    void anotherUserCannotUseTheProfileForARunOrAQuestion() {
        signInAs(bob);

        assertThatThrownBy(() -> service.resolveProfileId(alicesProfileId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.resolveSettings(alicesProfileId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anotherUserCannotTestChangeMakeDefaultOrDeleteIt() {
        signInAs(bob);

        assertThatThrownBy(() -> service.test(alicesProfileId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(alicesProfileId, save("Hijacked", "sk-bob-key")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.setDefault(alicesProfileId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(alicesProfileId)).isInstanceOf(NotFoundException.class);

        signInAs(alice);
        List<LlmProfileResponse> profiles = service.list();
        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).name()).isEqualTo("Alice's GPT");
        assertThat(service.resolveSettings(alicesProfileId).apiKey()).isEqualTo(ALICE_KEY);
    }

    @Test
    void aUserWithoutTheirOwnProfileNeverFallsBackToSomeoneElsesDefault() {
        // Alice's profile is her default; Bob leaving the choice open must not pick it up.
        signInAs(bob);

        assertThatThrownBy(() -> service.resolveSettings(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.resolveProfileId(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void eachUserKeepsTheirOwnDefaultAndCanReuseTheSameProfileName() {
        signInAs(bob);
        LlmProfileResponse bobs = service.create(save("Alice's GPT", "sk-bob-key"));

        assertThat(bobs.isDefault()).isTrue();
        assertThat(service.resolveSettings(null).apiKey()).isEqualTo("sk-bob-key");

        signInAs(alice);
        assertThat(service.resolveProfileId(null)).isEqualTo(alicesProfileId);
        assertThat(service.resolveSettings(null).apiKey()).isEqualTo(ALICE_KEY);
    }

    @Test
    void theApiKeyIsNeverReturnedOverTheApi() {
        LlmProfileResponse response = service.list().get(0);

        assertThat(response.toString()).doesNotContain(ALICE_KEY);
        assertThat(response.apiKeyMask()).isEqualTo("••••1234");
    }

    private void signInAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static SaveLlmProfileRequest save(String name, String apiKey) {
        return new SaveLlmProfileRequest(
                name, "https://api.example.com/v1", apiKey, "test-model", null, null, null);
    }
}
