package com.resumetailor.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class OpenAiCompatibleClientTest {

    private static final String COMPLETION = """
            {
              "model": "gpt-4o-mini",
              "choices": [{"message": {"role": "assistant", "content": "{\\"ok\\": true}"}}],
              "usage": {"prompt_tokens": 120, "completion_tokens": 45}
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenAiCompatibleClient client;

    private static LlmSettings settings() {
        return new LlmSettings("https://api.example.com/v1", "sk-test", "gpt-4o-mini",
                new BigDecimal("0.20"), 4000);
    }

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiCompatibleClient(new ObjectMapper(), builder.build());
    }

    @Test
    void sendsBearerAuthAndReturnsContentWithUsage() {
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"gpt-4o-mini\"")))
                .andRespond(withSuccess(COMPLETION, MediaType.APPLICATION_JSON));

        LlmChatResult result = client.chat(settings(), "system", "user", false);

        assertThat(result.content()).isEqualTo("{\"ok\": true}");
        assertThat(result.model()).isEqualTo("gpt-4o-mini");
        assertThat(result.promptTokens()).isEqualTo(120);
        assertThat(result.completionTokens()).isEqualTo(45);
        server.verify();
    }

    @Test
    void requestsJsonModeWhenAsked() {
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response_format")))
                .andRespond(withSuccess(COMPLETION, MediaType.APPLICATION_JSON));

        client.chat(settings(), "system", "user", true);

        server.verify();
    }

    @Test
    void retriesWithoutJsonModeWhenTheServerRejectsTheField() {
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withBadRequest()
                        .body("{\"error\": {\"message\": \"Unknown parameter: response_format\"}}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("response_format"))))
                .andRespond(withSuccess(COMPLETION, MediaType.APPLICATION_JSON));

        LlmChatResult result = client.chat(settings(), "system", "user", true);

        assertThat(result.content()).isEqualTo("{\"ok\": true}");
        server.verify();
    }

    @Test
    void surfacesTheProvidersOwnErrorMessage() {
        server.expect(twice(), requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withUnauthorizedRequest()
                        .body("{\"error\": {\"message\": \"Incorrect API key provided\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(settings(), "system", "user", false))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Incorrect API key provided");
    }

    @Test
    void rejectsAnEmptyCompletionWithActionableAdvice() {
        String empty = "{\"choices\": [{\"message\": {\"content\": \"\"}}]}";
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withSuccess(empty, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(settings(), "system", "user", false))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Max output tokens");
    }

    @Test
    void sendsAContentLengthInsteadOfAChunkedBody() {
        // Small OpenAI-compatible servers often cannot read chunked request bodies and
        // reset the connection instead -- found running the real stack end to end.
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(header("Content-Length", org.hamcrest.Matchers.matchesPattern("\\d+")))
                .andRespond(withSuccess(COMPLETION, MediaType.APPLICATION_JSON));

        client.chat(settings(), "system", "user", false);

        server.verify();
    }

    @Test
    void aConnectionDroppedMidResponseBecomesAnLlmExceptionNotABare500() {
        server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(request -> new org.springframework.mock.http.client.MockClientHttpResponse(
                        new java.io.InputStream() {
                            @Override
                            public int read() throws java.io.IOException {
                                throw new java.io.IOException("Connection reset");
                            }
                        },
                        org.springframework.http.HttpStatus.OK));

        assertThatThrownBy(() -> client.chat(settings(), "system", "user", false))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("closed the connection");
    }

    @Test
    void buildsTheEndpointFromVariedBaseUrlSpellings() {
        assertThat(OpenAiCompatibleClient.chatCompletionsUrl("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(OpenAiCompatibleClient.chatCompletionsUrl("https://api.openai.com/v1/"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(OpenAiCompatibleClient.chatCompletionsUrl("  http://localhost:11434/v1  "))
                .isEqualTo("http://localhost:11434/v1/chat/completions");
        assertThat(OpenAiCompatibleClient.chatCompletionsUrl("https://api.example.com/v1/chat/completions"))
                .isEqualTo("https://api.example.com/v1/chat/completions");
    }
}
