package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.llm.LlmException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

    @Test
    void parsesAWellFormedJdAnalysis() {
        String raw = """
                {
                  "company": "Acme",
                  "role": "Backend Engineer",
                  "keywords": [
                    {"keyword": "Kubernetes", "importance": "REQUIRED"},
                    {"keyword": "Terraform", "importance": "preferred"}
                  ],
                  "responsibilities": ["Run services"],
                  "mustHaves": ["5 years experience"]
                }
                """;

        JdAnalysisResult result = parser.parseJdAnalysis(raw, "gpt-4o-mini");

        assertThat(result.company()).isEqualTo("Acme");
        assertThat(result.keywords()).hasSize(2);
        assertThat(result.keywords().get(0).importance()).isEqualTo(KeywordImportance.REQUIRED);
        assertThat(result.keywords().get(1).importance()).isEqualTo(KeywordImportance.PREFERRED);
        assertThat(result.responsibilities()).containsExactly("Run services");
    }

    @Test
    void acceptsBareStringKeywordsAndDeduplicates() {
        String raw = """
                {"keywords": ["Java", "java", {"keyword": "Java"}, {"keyword": "Go", "importance": "weird"}]}
                """;

        JdAnalysisResult result = parser.parseJdAnalysis(raw, "local-model");

        assertThat(result.keywords()).hasSize(2);
        assertThat(result.keywords().get(0).importance()).isEqualTo(KeywordImportance.NICE);
        assertThat(result.keywords().get(1).keyword()).isEqualTo("Go");
    }

    @Test
    void toleratesMissingOptionalFields() {
        JdAnalysisResult result = parser.parseJdAnalysis("{\"keywords\": []}", "m");

        assertThat(result.company()).isNull();
        assertThat(result.responsibilities()).isEmpty();
        assertThat(result.mustHaves()).isEmpty();
    }

    @Test
    void parsesTailoringOutputIncludingChangesAndSuggestions() {
        String raw = """
                {
                  "tailoredBody": "\\\\section{Experience}\\nLed platform work across several teams and years.",
                  "changes": [{"section": "Experience", "changeType": "reworded", "rationale": "Matches the JD"}],
                  "suggestions": [
                    {"kind": "SKILL", "targetSection": "Skills", "content": "Terraform", "rationale": "JD requires it"}
                  ]
                }
                """;

        TailorOutput output = parser.parseTailorOutput(raw);

        assertThat(output.tailoredBody()).contains("Led platform work");
        assertThat(output.changes()).hasSize(1);
        assertThat(output.changes().get(0).changeType()).isEqualTo("REWORDED");
        assertThat(output.suggestions()).hasSize(1);
        assertThat(output.suggestions().get(0).kind()).isEqualTo(SuggestionKind.SKILL);
    }

    @Test
    void normalisesAmericanSpellingOfEmphasised() {
        String raw = """
                {"tailoredBody": "%s", "changes": [{"section": "X", "changeType": "EMPHASIZED"}]}
                """.formatted("a".repeat(60));

        assertThat(parser.parseTailorOutput(raw).changes().get(0).changeType()).isEqualTo("EMPHASISED");
    }

    @Test
    void failsLoudlyWhenTheModelOmitsTheBody() {
        assertThatThrownBy(() -> parser.parseTailorOutput("{\"changes\": []}"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("tailoredBody");
    }

    @Test
    void rejectsASuspiciouslyShortBodyRatherThanSavingAFragment() {
        assertThatThrownBy(() -> parser.parseTailorOutput("{\"tailoredBody\": \"too short\"}"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("fragment");
    }

    @Test
    void reportsMalformedJson() {
        assertThatThrownBy(() -> parser.parseTailorOutput("{\"tailoredBody\": }"))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void skipsSuggestionsWithNoContent() {
        String raw = """
                {"tailoredBody": "%s", "suggestions": [{"kind": "BULLET", "content": ""}]}
                """.formatted("a".repeat(60));

        assertThat(parser.parseTailorOutput(raw).suggestions()).isEmpty();
    }
}
