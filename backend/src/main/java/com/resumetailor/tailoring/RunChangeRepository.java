package com.resumetailor.tailoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RunChangeRepository extends JpaRepository<RunChange, UUID> {

    List<RunChange> findAllByRunIdOrderByOrdinalAsc(UUID runId);

    void deleteAllByRunId(UUID runId);
}
