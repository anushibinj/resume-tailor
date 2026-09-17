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

/** A JD keyword and whether it appears in each version. Presence is computed in Java. */
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

    @Column(name = "present_in_original", nullable = false)
    private boolean presentInOriginal;

    @Column(name = "present_in_tailored", nullable = false)
    private boolean presentInTailored;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;
}
