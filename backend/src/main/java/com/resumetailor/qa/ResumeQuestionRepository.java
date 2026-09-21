package com.resumetailor.qa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Every method is owner-scoped on purpose -- see
 * {@link com.resumetailor.user.CurrentUserProvider}.
 */
public interface ResumeQuestionRepository extends JpaRepository<ResumeQuestion, UUID> {

    Page<ResumeQuestion> findAllByOwnerIdAndResumeIdOrderByCreatedAtDesc(
            UUID ownerId, UUID resumeId, Pageable pageable);

    Optional<ResumeQuestion> findByIdAndOwnerIdAndResumeId(UUID id, UUID ownerId, UUID resumeId);
}
