package com.resumetailor.keyword;

import com.resumetailor.keyword.CoverageScorer.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageScorerTest {

    @Test
    void weightsRequiredAboveNiceToHave() {
        int score = CoverageScorer.score(List.of(
                new Requirement(KeywordImportance.REQUIRED, true),
                new Requirement(KeywordImportance.NICE, false)));

        // 3 of 4 available weight.
        assertThat(score).isEqualTo(75);
    }

    @Test
    void missingARequiredRequirementCostsMoreThanMissingANiceOne() {
        int missingRequired = CoverageScorer.score(List.of(
                new Requirement(KeywordImportance.REQUIRED, false),
                new Requirement(KeywordImportance.NICE, true)));
        int missingNice = CoverageScorer.score(List.of(
                new Requirement(KeywordImportance.REQUIRED, true),
                new Requirement(KeywordImportance.NICE, false)));

        assertThat(missingRequired).isLessThan(missingNice);
    }

    @Test
    void fullCoverageScoresOneHundred() {
        assertThat(CoverageScorer.score(List.of(
                new Requirement(KeywordImportance.REQUIRED, true),
                new Requirement(KeywordImportance.PREFERRED, true)))).isEqualTo(100);
    }

    @Test
    void returnsZeroRatherThanDividingByZeroWhenNothingWasExtracted() {
        assertThat(CoverageScorer.score(List.of())).isZero();
    }
}
