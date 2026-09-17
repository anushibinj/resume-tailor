package com.resumetailor.tailoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RunKeywordRepository extends JpaRepository<RunKeyword, UUID> {

    List<RunKeyword> findAllByRunIdOrderByOrdinalAsc(UUID runId);

    void deleteAllByRunId(UUID runId);
}
