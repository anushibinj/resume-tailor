package com.resumetailor.skill;

import com.resumetailor.keyword.KeywordNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The shared glossary of skills. A definition is looked up by the requirement's
 * normalised wording and, once written, never rewritten: a first description that is
 * good enough is better than one that changes under every user's feet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillDefinitionService {

    /** Keeps a model that rambles from filling the tooltip. */
    static final int MAX_DESCRIPTION_CHARS = 500;

    private final SkillDefinitionRepository repository;

    /** A description for a requirement, by its wording. */
    public record Learned(String name, String description) {
    }

    /** Known descriptions, keyed by {@link KeywordNormalizer#normalize} of the requirement. */
    @Transactional(readOnly = true)
    public Map<String, String> descriptionsFor(Collection<String> names) {
        List<String> keys = names.stream()
                .map(KeywordNormalizer::normalize)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByNameKeyIn(keys).stream()
                .collect(Collectors.toMap(SkillDefinition::getNameKey, SkillDefinition::getDescription, (a, b) -> a));
    }

    /**
     * Stores descriptions not already known. Deliberately not one transaction: two users'
     * runs can meet the same new skill at once, and the loser of that race hits the unique
     * key. That must cost only its own row, never the rest or the gap check that called it.
     */
    public void remember(List<Learned> learned, String modelUsed) {
        for (Learned item : learned) {
            String key = KeywordNormalizer.normalize(item.name());
            String description = item.description() == null ? "" : item.description().strip();
            if (key.isBlank() || description.isBlank() || repository.existsByNameKey(key)) {
                continue;
            }
            SkillDefinition definition = new SkillDefinition();
            definition.setNameKey(key);
            definition.setName(item.name().strip());
            definition.setDescription(description.length() <= MAX_DESCRIPTION_CHARS
                    ? description
                    : description.substring(0, MAX_DESCRIPTION_CHARS).strip() + "…");
            definition.setModelUsed(modelUsed);
            try {
                repository.save(definition);
            } catch (DataIntegrityViolationException raced) {
                log.debug("Skill '{}' was described concurrently; keeping the existing definition", key);
            }
        }
    }
}
