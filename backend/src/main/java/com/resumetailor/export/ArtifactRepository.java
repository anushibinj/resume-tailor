package com.resumetailor.export;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    Optional<Artifact> findFirstByRunIdAndKindOrderByCreatedAtDesc(UUID runId, String kind);

    void deleteAllByRunId(UUID runId);
}
