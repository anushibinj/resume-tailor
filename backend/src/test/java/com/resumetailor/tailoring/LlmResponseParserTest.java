package com.resumetailor.tailoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.keyword.JdKeyword;
import com.resumetailor.keyword.KeywordImportance;
import com.resumetailor.llm.LlmException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    void parsesTailoringOutputIncludingChanges() {
        String raw = """
                {
                  "tailoredBody": "\\\\section{Experience}\\nLed platform work across several teams and years.",
                  "changes": [{"section": "Experience", "changeType": "reworded", "rationale": "Matches the JD"}]
                }
                """;

        TailorOutput output = parser.parseTailorOutput(raw);

        assertThat(output.tailoredBody()).contains("Led platform work");
        assertThat(output.changes()).hasSize(1);
        assertThat(output.changes().get(0).changeType()).isEqualTo("REWORDED");
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


    // ------------------------------------------------------------------ gap analysis

    private static final List<JdKeyword> REQUIREMENTS = List.of(
            new JdKeyword("Java", KeywordImportance.REQUIRED),
            new JdKeyword("Go", KeywordImportance.PREFERRED),
            new JdKeyword("Kafka", KeywordImportance.NICE));

    @Test
    void parsesCoverageWithEvidenceAndAnAnchoredAddition() {
        String raw = """
                {"requirements": [
                  {"keyword": "Java", "covered": true, "evidence": "Built the billing service in Java"},
                  {"keyword": "Go", "covered": false,
                   "addition": {"label": "Go", "kind": "SKILL", "section": "Technical Skills",
                                "anchor": "Docker, Kubernetes", "placement": "AFTER", "insert": ", Go"}},
                  {"keyword": "Kafka", "covered": false,
                   "addition": {"label": "Kafka", "kind": "BULLET", "section": "Experience",
                                "insert": "Ran Kafka pipelines"}}
                ]}
                """;

        List<GapAnalysisResult.RequirementVerdict> verdicts =
                parser.parseGapAnalysis(raw, REQUIREMENTS, Set.of(), "m").verdicts();

        assertThat(verdicts).hasSize(3);
        assertThat(verdicts.get(0).covered()).isTrue();
        assertThat(verdicts.get(0).evidence()).isEqualTo("Built the billing service in Java");
        assertThat(verdicts.get(0).addition()).isNull();

        GapAnalysisResult.ProposedAddition go = verdicts.get(1).addition();
        assertThat(verdicts.get(1).covered()).isFalse();
        assertThat(go.anchor()).isEqualTo("Docker, Kubernetes");
        assertThat(go.placement()).isEqualTo(Placement.AFTER);
        assertThat(go.insert()).isEqualTo(", Go");
        assertThat(go.kind()).isEqualTo(SuggestionKind.SKILL);

        // No anchor means placement is meaningless and the app places it by section.
        assertThat(verdicts.get(2).addition().anchor()).isNull();
        assertThat(verdicts.get(2).addition().placement()).isNull();
    }

    @Test
    void everyRequirementGetsAVerdictEvenWhenTheModelDropsOne() {
        String raw = """
                {"requirements": [{"keyword": "Java", "covered": true, "evidence": "Java everywhere"}]}
                """;

        List<GapAnalysisResult.RequirementVerdict> verdicts =
                parser.parseGapAnalysis(raw, REQUIREMENTS, Set.of(), "m").verdicts();

        assertThat(verdicts).extracting(v -> v.requirement().keyword())
                .containsExactly("Java", "Go", "Kafka");
        // A dropped requirement must still be actionable, not silently absent.
        assertThat(verdicts.get(1).covered()).isFalse();
        assertThat(verdicts.get(1).addition().label()).isEqualTo("Go");
        assertThat(verdicts.get(1).addition().kind()).isEqualTo(SuggestionKind.SKILL);
    }

    @Test
    void anUncoveredRequirementAlwaysComesWithSomethingToAdd() {
        String raw = """
                {"requirements": [
                  {"keyword": "Java", "covered": false},
                  {"keyword": "Go", "covered": false, "addition": {}},
                  {"keyword": "Kafka", "covered": false, "addition": {"label": "", "insert": ""}}
                ]}
                """;

        List<GapAnalysisResult.RequirementVerdict> verdicts =
                parser.parseGapAnalysis(raw, REQUIREMENTS, Set.of(), "m").verdicts();

        assertThat(verdicts).allSatisfy(v -> assertThat(v.addition()).isNotNull());
        assertThat(verdicts).extracting(v -> v.addition().label())
                .containsExactly("Java", "Go", "Kafka");
    }

    @Test
    void pointsAtAnAdditionTheUserAlreadyAcceptedInsteadOfProposingItAgain() {
        UUID existing = UUID.randomUUID();
        String raw = """
                {"requirements": [{"keyword": "Go", "covered": false, "addressedBy": "%s"}]}
                """.formatted(existing);

        GapAnalysisResult.RequirementVerdict verdict = parser
                .parseGapAnalysis(raw, List.of(REQUIREMENTS.get(1)), Set.of(existing), "m")
                .verdicts().get(0);

        assertThat(verdict.addressedBy()).isEqualTo(existing);
        assertThat(verdict.addition()).isNull();
    }

    @Test
    void ignoresAnUnknownAddressedByAndOffersAnAdditionInstead() {
        String raw = """
                {"requirements": [{"keyword": "Go", "covered": false, "addressedBy": "%s"}]}
                """.formatted(UUID.randomUUID());

        GapAnalysisResult.RequirementVerdict verdict = parser
                .parseGapAnalysis(raw, List.of(REQUIREMENTS.get(1)), Set.of(), "m")
                .verdicts().get(0);

        assertThat(verdict.addressedBy()).isNull();
        assertThat(verdict.addition().label()).isEqualTo("Go");
    }

    @Test
    void matchesRequirementsBackDespiteCaseAndSpacingDifferences() {
        String raw = """
                {"requirements": [{"keyword": "  java ", "covered": true, "evidence": "Java"}]}
                """;

        assertThat(parser.parseGapAnalysis(raw, List.of(REQUIREMENTS.get(0)), Set.of(), "m")
                .verdicts().get(0).covered()).isTrue();
    }

    @Test
    void reportsMalformedGapJson() {
        assertThatThrownBy(() -> parser.parseGapAnalysis("not json", REQUIREMENTS, Set.of(), "m"))
                .isInstanceOf(LlmException.class);
    }
}
