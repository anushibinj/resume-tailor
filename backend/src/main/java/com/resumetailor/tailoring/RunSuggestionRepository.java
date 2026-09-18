package com.resumetailor.tailoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunSuggestionRepository extends JpaRepository<RunSuggestion, UUID> {

    List<RunSuggestion> findAllByRunIdOrderByOrdinalAsc(UUID runId);

    List<RunSuggestion> findAllByRunIdAndStatusOrderByOrdinalAsc(UUID runId, SuggestionStatus status);

    Optional<RunSuggestion> findByIdAndRunId(UUID id, UUID runId);

    void deleteAllByRunId(UUID runId);

    /** Used when regenerating gaps: anything the user accepted is part of their document. */
    void deleteAllByRunIdAndStatusNot(UUID runId, SuggestionStatus status);
}
