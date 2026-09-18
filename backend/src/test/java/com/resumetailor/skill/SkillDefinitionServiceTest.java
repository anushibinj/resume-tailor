package com.resumetailor.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillDefinitionServiceTest {

    private SkillDefinitionRepository repository;
    private SkillDefinitionService service;

    @BeforeEach
    void setUp() {
        repository = mock(SkillDefinitionRepository.class);
        service = new SkillDefinitionService(repository);
    }

    private static SkillDefinition definition(String key, String description) {
        SkillDefinition definition = new SkillDefinition();
        definition.setNameKey(key);
        definition.setName(key);
        definition.setDescription(description);
        return definition;
    }

    @Test
    void looksDescriptionsUpByTheNormalisedWording() {
        when(repository.findAllByNameKeyIn(any())).thenReturn(List.of(definition("java", "A JVM language.")));

        Map<String, String> found = service.descriptionsFor(List.of("Java (Programming Language)", "Java"));

        assertThat(found).containsExactly(Map.entry("java", "A JVM language."));
        ArgumentCaptor<java.util.Collection<String>> keys = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(repository).findAllByNameKeyIn(keys.capture());
        assertThat(keys.getValue()).containsExactly("java");
    }

    @Test
    void savesANewDescriptionUnderTheNormalisedKey() {
        service.remember(List.of(new SkillDefinitionService.Learned(
                "Cloud-Native Design Patterns (CNCF)", "Ways of building apps for the cloud.")), "gpt-x");

        ArgumentCaptor<SkillDefinition> saved = ArgumentCaptor.forClass(SkillDefinition.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getNameKey()).isEqualTo("cloud-native design patterns");
        assertThat(saved.getValue().getName()).isEqualTo("Cloud-Native Design Patterns (CNCF)");
        assertThat(saved.getValue().getModelUsed()).isEqualTo("gpt-x");
    }

    @Test
    void neverOverwritesAnExistingDefinition() {
        when(repository.existsByNameKey("java")).thenReturn(true);

        service.remember(List.of(new SkillDefinitionService.Learned("Java", "A different wording.")), "m");

        verify(repository, never()).save(any());
    }

    @Test
    void skipsBlankDescriptionsAndCapsRamblingOnes() {
        service.remember(List.of(
                new SkillDefinitionService.Learned("Go", "   "),
                new SkillDefinitionService.Learned("Rust", "x".repeat(2000))), "m");

        ArgumentCaptor<SkillDefinition> saved = ArgumentCaptor.forClass(SkillDefinition.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getNameKey()).isEqualTo("rust");
        assertThat(saved.getValue().getDescription().length())
                .isLessThanOrEqualTo(SkillDefinitionService.MAX_DESCRIPTION_CHARS + 1);
    }

    @Test
    void losingARaceToAnotherUsersRunCostsOnlyThatRow() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate name_key"));

        assertThatCode(() -> service.remember(List.of(
                new SkillDefinitionService.Learned("Go", "A language."),
                new SkillDefinitionService.Learned("Rust", "Another language.")), "m"))
                .doesNotThrowAnyException();
        verify(repository, times(2)).save(any());
    }
}
