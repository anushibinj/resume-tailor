package com.resumetailor.jd;

import com.resumetailor.common.Hashing;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.user.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobDescriptionService {

    private final JobDescriptionRepository repository;
    private final JdAnalysisRepository analysisRepository;
    private final CurrentUserProvider currentUser;

    /**
     * Stores a pasted JD. Identical text submitted again reuses the existing row so its
     * cached analysis is reused too -- re-running the same JD should not cost another
     * extraction call.
     */
    @Transactional
    public JobDescription findOrCreate(String company, String role, String sourceUrl, String rawText) {
        UUID ownerId = currentUser.currentUserId();
        String hash = Hashing.sha256Hex(rawText);

        Optional<JobDescription> existing =
                repository.findAllByOwnerIdAndContentHashOrderByCreatedAtDesc(ownerId, hash)
                        .stream()
                        .findFirst();
        if (existing.isPresent()) {
            JobDescription jd = existing.get();
            // Keep the newest labels the user typed; the JD body is what the hash pins.
            if (company != null && !company.isBlank()) {
                jd.setCompany(company.trim());
            }
            if (role != null && !role.isBlank()) {
                jd.setRole(role.trim());
            }
            return repository.save(jd);
        }

        JobDescription jd = new JobDescription();
        jd.setOwnerId(ownerId);
        jd.setCompany(company == null || company.isBlank() ? null : company.trim());
        jd.setRole(role == null || role.isBlank() ? null : role.trim());
        jd.setSourceUrl(sourceUrl == null || sourceUrl.isBlank() ? null : sourceUrl.trim());
        jd.setRawText(rawText);
        jd.setContentHash(hash);
        return repository.save(jd);
    }

    @Transactional(readOnly = true)
    public JobDescription require(UUID id) {
        return repository.findByIdAndOwnerId(id, currentUser.currentUserId())
                .orElseThrow(() -> NotFoundException.of("Job description", id));
    }

    @Transactional(readOnly = true)
    public Optional<JdAnalysis> findAnalysis(UUID jobDescriptionId) {
        return analysisRepository.findByJobDescriptionId(jobDescriptionId);
    }

    @Transactional
    public JdAnalysis saveAnalysis(JdAnalysis analysis) {
        return analysisRepository.save(analysis);
    }

    @Transactional
    public void updateLabels(UUID jobDescriptionId, String company, String role) {
        repository.findById(jobDescriptionId).ifPresent(jd -> {
            // Only fill blanks: a label the user typed always wins over one the model guessed.
            if ((jd.getCompany() == null || jd.getCompany().isBlank()) && company != null && !company.isBlank()) {
                jd.setCompany(company.trim());
            }
            if ((jd.getRole() == null || jd.getRole().isBlank()) && role != null && !role.isBlank()) {
                jd.setRole(role.trim());
            }
            repository.save(jd);
        });
    }
}
