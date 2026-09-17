package com.resumetailor.llm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owner-scoped; see {@link com.resumetailor.user.CurrentUserProvider}. */
public interface LlmProfileRepository extends JpaRepository<LlmProfile, UUID> {

    List<LlmProfile> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<LlmProfile> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<LlmProfile> findByOwnerIdAndIsDefaultTrue(UUID ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(UUID ownerId, String name, UUID id);

    long countByOwnerId(UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LlmProfile p set p.isDefault = false where p.ownerId = :ownerId and p.isDefault = true")
    void clearDefault(@Param("ownerId") UUID ownerId);
}
