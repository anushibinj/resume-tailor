package com.resumetailor.keyword;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordNormalizerTest {

    @Test
    void differentWordingsOfOneRequirementShareAKey() {
        assertThat(KeywordNormalizer.normalize("Java (Programming Language)")).isEqualTo("java");
        assertThat(KeywordNormalizer.normalize("Java (REQUIRED)")).isEqualTo("java");
        assertThat(KeywordNormalizer.normalize("  JAVA. ")).isEqualTo("java");
        assertThat(KeywordNormalizer.normalize("Cloud-Native   Design Patterns"))
                .isEqualTo("cloud-native design patterns");
    }

    @Test
    void doesNotEatTheSymbolsThatMakeASkillDistinct() {
        // Distinct skills must not share a key, or one's description would show for another.
        assertThat(KeywordNormalizer.normalize("C++")).isEqualTo("c++");
        assertThat(KeywordNormalizer.normalize("C#")).isEqualTo("c#");
        assertThat(KeywordNormalizer.normalize("C")).isEqualTo("c");
        assertThat(KeywordNormalizer.normalize("C++, ")).isEqualTo("c++");
    }
}
