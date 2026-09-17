package com.resumetailor.tailoring;

import com.resumetailor.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Content the model thinks the JD wants but the resume does not claim.
 *
 * <p>Suggestions are never spliced into the tailored resume automatically. They stay
 * {@link SuggestionStatus#PROPOSED} until the user accepts one, because accepting is
 * the user asserting the claim is true -- that is not a decision a model gets to make.
 */
@Entity
@Table(name = "run_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class RunSuggestion extends AuditedEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private SuggestionKind kind;

    @Column(name = "target_section", length = 512)
    private String targetSection;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SuggestionStatus status;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;
}
