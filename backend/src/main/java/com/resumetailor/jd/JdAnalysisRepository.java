package com.resumetailor.jd;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JdAnalysisRepository extends JpaRepository<JdAnalysis, UUID> {

    Optional<JdAnalysis> findByJobDescriptionId(UUID jobDescriptionId);
}
