package com.resumetailor.tailoring;

import com.resumetailor.common.BaseEntity;
import com.resumetailor.keyword.KeywordImportance;
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
 * One job requirement and whether the tailored resume covers it.
 *
 * <p>{@code covered} is the model's judgment, with {@code evidence} quoted from the
 * resume so the user can check it. A requirement whose linked suggestion has been
 * accepted also counts as covered -- that is computed at read time by
 * {@link com.resumetailor.keyword.CoverageScorer}, never stored, so removing an addition
 * reverts the score without another model call.
 */
@Entity
@Table(name = "run_keywords")
@Getter
@Setter
@NoArgsConstructor
public class RunKeyword extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 16)
    private KeywordImportance importance;

    @Column(name = "covered", nullable = false)
    private boolean covered;

    /** Short quote from the resume backing a "covered" judgment. */
    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    /** The addition offered for this gap, if any. */
    @Column(name = "suggestion_id")
    private UUID suggestionId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;
}
