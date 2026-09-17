package com.resumetailor.jd;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owner-scoped; see {@link com.resumetailor.user.CurrentUserProvider}. */
public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {

    Optional<JobDescription> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<JobDescription> findAllByOwnerIdAndContentHashOrderByCreatedAtDesc(UUID ownerId, String contentHash);
}
