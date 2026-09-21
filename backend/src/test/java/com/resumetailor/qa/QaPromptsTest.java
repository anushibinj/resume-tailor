package com.resumetailor.qa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answer is meant to be pasted somewhere as the candidate's own words, so the voice
 * rule is a product promise, not wording -- pinned here so a prompt edit can't quietly
 * turn it back into a third-party summary addressed to "you".
 */
class QaPromptsTest {

    @Test
    void asksForTheCandidatesOwnFirstPersonVoice() {
        assertThat(QaPrompts.SYSTEM)
                .contains("first person")
                .contains("My core")
                .contains("Never address the candidate as \"you\"");
    }

    @Test
    void asksForTheAnswerAloneWithNoLeadIn() {
        assertThat(QaPrompts.SYSTEM).contains("Give only the answer itself");
    }

    @Test
    void stillForbidsInventingFactsNotInTheResume() {
        assertThat(QaPrompts.SYSTEM).contains("Never invent");
    }

    @Test
    void userPromptCarriesTheResumeAndTheQuestion() {
        assertThat(QaPrompts.user("Built billing in Java", "What do I know?"))
                .contains("Built billing in Java")
                .contains("What do I know?");
    }
}
