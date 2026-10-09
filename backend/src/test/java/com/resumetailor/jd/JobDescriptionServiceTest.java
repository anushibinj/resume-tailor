package com.resumetailor.jd;

import com.resumetailor.common.Hashing;
import com.resumetailor.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JobDescriptionServiceTest {

    @Mock
    private JobDescriptionRepository repository;

    @Mock
    private JdAnalysisRepository analysisRepository;

    @Mock
    private CurrentUserProvider currentUser;

    private JobDescriptionService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JobDescriptionService(repository, analysisRepository, currentUser);
        given(currentUser.currentUserId()).willReturn(ownerId);
    }

    @Test
    void findOrCreateSetsJobUrlWhenSupplied() {
        String rawText = "We need a senior engineer.";
        String hash = Hashing.sha256Hex(rawText);
        given(repository.findAllByOwnerIdAndContentHashOrderByCreatedAtDesc(ownerId, hash))
                .willReturn(List.of());
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        JobDescription jd = service.findOrCreate("Acme", "Lead Dev", "https://acme.com/jobs/lead", rawText);

        assertThat(jd.getCompany()).isEqualTo("Acme");
        assertThat(jd.getRole()).isEqualTo("Lead Dev");
        assertThat(jd.getSourceUrl()).isEqualTo("https://acme.com/jobs/lead");
        assertThat(jd.getJobUrl()).isEqualTo("https://acme.com/jobs/lead");
        assertThat(jd.getRawText()).isEqualTo(rawText);
    }

    @Test
    void findOrCreateTrimsJobUrlAndHandlesEmptyAsNull() {
        String rawText = "We need a developer.";
        String hash = Hashing.sha256Hex(rawText);
        given(repository.findAllByOwnerIdAndContentHashOrderByCreatedAtDesc(ownerId, hash))
                .willReturn(List.of());
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        JobDescription jdWithSpaces = service.findOrCreate("  Acme  ", "  Lead  ", "  https://example.com/job  ", rawText);
        assertThat(jdWithSpaces.getSourceUrl()).isEqualTo("https://example.com/job");
        assertThat(jdWithSpaces.getJobUrl()).isEqualTo("https://example.com/job");

        JobDescription jdBlank = service.findOrCreate(null, null, "   ", rawText);
        assertThat(jdBlank.getSourceUrl()).isNull();
        assertThat(jdBlank.getJobUrl()).isNull();
    }

    @Test
    void findOrCreateUpdatesJobUrlOnExistingRecord() {
        String rawText = "Same JD text.";
        String hash = Hashing.sha256Hex(rawText);

        JobDescription existing = new JobDescription();
        existing.setOwnerId(ownerId);
        existing.setRawText(rawText);
        existing.setContentHash(hash);
        existing.setSourceUrl(null);

        given(repository.findAllByOwnerIdAndContentHashOrderByCreatedAtDesc(ownerId, hash))
                .willReturn(List.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        JobDescription updated = service.findOrCreate("Acme", "Lead", "https://new-url.com", rawText);

        assertThat(updated.getSourceUrl()).isEqualTo("https://new-url.com");
        assertThat(updated.getJobUrl()).isEqualTo("https://new-url.com");
        assertThat(updated.getCompany()).isEqualTo("Acme");
        assertThat(updated.getRole()).isEqualTo("Lead");
    }
}
