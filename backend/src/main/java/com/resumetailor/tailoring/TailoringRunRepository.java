package com.resumetailor.tailoring;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Owner-scoped; see {@link com.resumetailor.user.CurrentUserProvider}. */
public interface TailoringRunRepository extends JpaRepository<TailoringRun, UUID> {

    Page<TailoringRun> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Page<TailoringRun> findAllByOwnerIdAndAppliedOrderByCreatedAtDesc(UUID ownerId, boolean applied, Pageable pageable);

    Optional<TailoringRun> findByIdAndOwnerId(UUID id, UUID ownerId);
}
