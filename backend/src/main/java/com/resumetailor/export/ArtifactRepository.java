package com.resumetailor.export;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    Optional<Artifact> findFirstByRunIdAndKindOrderByCreatedAtDesc(UUID runId, String kind);

    List<Artifact> findAllByRunId(UUID runId);

    List<Artifact> findAllByRunIdAndKind(UUID runId, String kind);

    void deleteAllByRunId(UUID runId);
}
