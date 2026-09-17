package com.resumetailor.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMatcherTest {

    private final KeywordMatcher matcher = new KeywordMatcher();

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void stripsLatexMarkupSoContentIsComparable() {
            String normalized = matcher.normalize("\\textbf{Built} a \\emph{Kubernetes} platform");

            assertThat(normalized).contains(" kubernetes ");
            assertThat(normalized).doesNotContain("textbf");
        }

        @Test
        void keepsMarkdownLinkTextAndDropsTheUrl() {
            String normalized = matcher.normalize("See [my Kubernetes work](https://example.com/k8s)");

            assertThat(normalized).contains(" kubernetes ");
            assertThat(normalized).doesNotContain("example.com");
        }

        @Test
        void preservesCharactersThatAreRealKeywords() {
            String normalized = matcher.normalize("Skills: C++, C#, .NET, Node.js");

            assertThat(normalized).contains(" c++ ").contains(" c# ").contains(" .net ").contains(" node.js ");
        }
    }

    @Nested
    @DisplayName("presence")
    class Presence {

        @Test
        void findsAKeywordPresentInTheResume() {
            String resume = matcher.normalize("Deployed services on Kubernetes at scale");

            assertThat(matcher.matches("Kubernetes", resume)).isTrue();
        }

        @Test
        void missesAKeywordTheResumeNeverClaims() {
            String resume = matcher.normalize("Deployed services on Kubernetes at scale");

            assertThat(matcher.matches("Terraform", resume)).isFalse();
        }

        @Test
        void matchesOnTokenBoundariesNotSubstrings() {
            String resume = matcher.normalize("Built React components");

            // "R" must not match inside "React" -- short keywords would otherwise match everywhere.
            assertThat(matcher.matches("R", resume)).isFalse();
            assertThat(matcher.matches("React", resume)).isTrue();
        }

        @Test
        void toleratesSingularPluralDifferences() {
            String resume = matcher.normalize("Designed REST APIs and microservices");

            assertThat(matcher.matches("API", resume)).isTrue();
            assertThat(matcher.matches("microservice", resume)).isTrue();
        }

        @Test
        void matchesMultiWordKeywordsAsAPhrase() {
            String resume = matcher.normalize("Experience with CI/CD pipelines and distributed systems");

            assertThat(matcher.matches("CI/CD", resume)).isTrue();
            assertThat(matcher.matches("distributed systems", resume)).isTrue();
            assertThat(matcher.matches("systems distributed", resume)).isFalse();
        }

        @Test
        void matchesKeywordsAcrossPunctuationBoundaries() {
            String resume = matcher.normalize("Kubernetes-based orchestration, Java/Spring stack");

            assertThat(matcher.matches("Kubernetes", resume)).isTrue();
            assertThat(matcher.matches("Spring", resume)).isTrue();
        }

        @Test
        void handlesKeywordsContainingSymbols() {
            String resume = matcher.normalize("Wrote C++ and C\\# services on .NET");

            assertThat(matcher.matches("C++", resume)).isTrue();
            assertThat(matcher.matches(".NET", resume)).isTrue();
        }

        @Test
        void matchesAKeywordThatEndsASentence() {
            // Resume bullets end in periods constantly; "Kubernetes." is still Kubernetes.
            String resume = matcher.normalize("Ran the platform on Kubernetes. Owned rollout.");

            assertThat(matcher.matches("Kubernetes", resume)).isTrue();
            assertThat(matcher.matches("rollout", resume)).isTrue();
        }

        @Test
        void sentencePunctuationDoesNotBreakSymbolKeywords() {
            String resume = matcher.normalize("Shipped services in C++. Also used .NET and Node.js.");

            assertThat(matcher.matches("C++", resume)).isTrue();
            assertThat(matcher.matches(".NET", resume)).isTrue();
            assertThat(matcher.matches("Node.js", resume)).isTrue();
        }

        @Test
        void ignoresBlankKeywords() {
            assertThat(matcher.matches("   ", matcher.normalize("anything"))).isFalse();
        }
    }

    @Nested
    @DisplayName("scoring")
    class Scoring {

        @Test
        void weightsRequiredKeywordsAboveNiceToHaves() {
            List<KeywordMatch> matches = List.of(
                    new KeywordMatch("Java", KeywordImportance.REQUIRED, true, true),
                    new KeywordMatch("Rust", KeywordImportance.NICE, false, false));

            // 3 of 4 available weight.
            assertThat(matcher.score(matches)).isEqualTo(75);
        }

        @Test
        void missingARequiredKeywordCostsMoreThanMissingANiceOne() {
            int missingRequired = matcher.score(List.of(
                    new KeywordMatch("Java", KeywordImportance.REQUIRED, false, false),
                    new KeywordMatch("Rust", KeywordImportance.NICE, true, true)));
            int missingNice = matcher.score(List.of(
                    new KeywordMatch("Java", KeywordImportance.REQUIRED, true, true),
                    new KeywordMatch("Rust", KeywordImportance.NICE, false, false)));

            assertThat(missingRequired).isLessThan(missingNice);
        }

        @Test
        void returnsZeroRatherThanDividingByZeroWhenNoKeywordsWereExtracted() {
            assertThat(matcher.score(List.of())).isZero();
        }

        @Test
        void fullCoverageScoresOneHundred() {
            List<KeywordMatch> matches = List.of(
                    new KeywordMatch("Java", KeywordImportance.REQUIRED, true, true),
                    new KeywordMatch("AWS", KeywordImportance.PREFERRED, true, true));

            assertThat(matcher.score(matches)).isEqualTo(100);
        }
    }

    @Test
    void reportsWhichKeywordsTailoringNewlySurfaced() {
        List<JdKeyword> keywords = List.of(
                new JdKeyword("Kubernetes", KeywordImportance.REQUIRED),
                new JdKeyword("Terraform", KeywordImportance.PREFERRED));

        List<KeywordMatch> matches = matcher.match(
                keywords,
                "Managed container platforms",
                "Managed container platforms on Kubernetes");

        assertThat(matches.get(0).presentInOriginal()).isFalse();
        assertThat(matches.get(0).presentInTailored()).isTrue();
        assertThat(matches.get(0).gained()).isTrue();
        assertThat(matches.get(1).presentInTailored()).isFalse();
    }
}
