package com.resumetailor.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every method is owner-scoped on purpose. Do not add an unscoped {@code findAll} --
 * see {@link com.resumetailor.user.CurrentUserProvider}.
 */
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Resume> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Resume> findByOwnerIdAndIsDefaultTrue(UUID ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(UUID ownerId, String name, UUID id);

    long countByOwnerId(UUID ownerId);

    /** Clears the current default so the partial unique index is never violated. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Resume r set r.isDefault = false where r.ownerId = :ownerId and r.isDefault = true")
    void clearDefault(@Param("ownerId") UUID ownerId);
}
