package com.resumetailor.skill;

import com.resumetailor.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * What a skill or requirement is, in plain words.
 *
 * <p>Shared across all users on purpose -- there is no {@code owner_id}. It is general
 * knowledge about a term, never anything about a candidate, so it is written once and
 * reused by every later posting that asks for the same thing.
 */
@Entity
@Table(name = "skill_definitions")
@Getter
@Setter
@NoArgsConstructor
public class SkillDefinition extends BaseEntity {

    /** {@link com.resumetailor.keyword.KeywordNormalizer} form of the name; unique. */
    @Column(name = "name_key", nullable = false, updatable = false)
    private String nameKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
