package com.resumetailor.tailoring;

import com.resumetailor.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** What the model changed in one section, and why -- shown as a chip in the diff view. */
@Entity
@Table(name = "run_changes")
@Getter
@Setter
@NoArgsConstructor
public class RunChange extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "section_title", length = 512)
    private String sectionTitle;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;
}
