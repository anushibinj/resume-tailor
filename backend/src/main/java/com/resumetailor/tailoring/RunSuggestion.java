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
 * Something the posting asks for that the resume does not show, offered as a one-click
 * addition. The user decides whether it goes in; nothing is applied automatically.
 *
 * <p>Placement is anchored: {@link #anchor} is text already present in the resume and
 * {@link #insertText} goes immediately after or before it, so the addition lands inside
 * the template's own structure. This makes accepting strictly additive -- it can insert
 * but never delete. Rows without an anchor fall back to section-based placement.
 */
@Entity
@Table(name = "run_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class RunSuggestion extends AuditedEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    /** The job requirement this addresses; null for suggestions made before V2. */
    @Column(name = "keyword")
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private SuggestionKind kind;

    @Column(name = "target_section", length = 512)
    private String targetSection;

    /** Human-readable label shown in the UI, e.g. "Go". */
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    /** Verbatim snippet of the resume to attach to; null falls back to section placement. */
    @Column(name = "anchor", columnDefinition = "text")
    private String anchor;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement", length = 8)
    private Placement placement;

    /** Exact markup inserted at the anchor, e.g. ", Go". */
    @Column(name = "insert_text", columnDefinition = "text")
    private String insertText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SuggestionStatus status;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;
}
