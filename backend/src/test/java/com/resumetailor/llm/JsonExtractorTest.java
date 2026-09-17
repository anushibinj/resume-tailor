package com.resumetailor.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonExtractorTest {

    @Test
    void passesThroughCleanJson() {
        assertThat(JsonExtractor.extractObject("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    void stripsMarkdownCodeFences() {
        String raw = """
                ```json
                {"tailoredBody": "hello"}
                ```""";

        assertThat(JsonExtractor.extractObject(raw)).isEqualTo("{\"tailoredBody\": \"hello\"}");
    }

    @Test
    void recoversJsonFollowingAChattyPreamble() {
        String raw = "Sure! Here is the tailored resume:\n{\"tailoredBody\": \"hello\"}";

        assertThat(JsonExtractor.extractObject(raw)).isEqualTo("{\"tailoredBody\": \"hello\"}");
    }

    @Test
    void handlesNestedObjectsAndArrays() {
        String raw = "prefix {\"a\": {\"b\": [1, 2, {\"c\": 3}]}} suffix";

        assertThat(JsonExtractor.extractObject(raw)).isEqualTo("{\"a\": {\"b\": [1, 2, {\"c\": 3}]}}");
    }

    @Test
    void ignoresBracesInsideStringValues() {
        String raw = "{\"body\": \"\\\\section{Experience} and }{ braces\"}";

        assertThat(JsonExtractor.extractObject(raw)).isEqualTo(raw);
    }

    @Test
    void reportsTruncationDistinctlySoTheUserCanRaiseTheTokenLimit() {
        assertThatThrownBy(() -> JsonExtractor.extractObject("{\"tailoredBody\": \"unterminated"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("output token limit");
    }

    @Test
    void reportsAnEmptyResponse() {
        assertThatThrownBy(() -> JsonExtractor.extractObject("  "))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void reportsAResponseWithNoJsonAtAll() {
        assertThatThrownBy(() -> JsonExtractor.extractObject("I cannot help with that."))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("no JSON object");
    }
}
