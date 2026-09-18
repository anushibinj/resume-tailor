package com.resumetailor.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Not owner-scoped, unlike every other repository: definitions are shared by all users.
 * See {@link SkillDefinition}.
 */
public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, UUID> {

    List<SkillDefinition> findAllByNameKeyIn(Collection<String> nameKeys);

    boolean existsByNameKey(String nameKey);
}
