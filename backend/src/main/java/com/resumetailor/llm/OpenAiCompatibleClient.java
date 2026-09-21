package com.resumetailor.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.config.LlmProperties;
import com.resumetailor.config.LlmQueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Talks to any endpoint exposing OpenAI's {@code POST /chat/completions}.
 *
 * <p>Two compatibility behaviours are deliberate:
 * <ul>
 *   <li>{@code response_format: json_object} is requested when we need JSON, but a
 *       server that rejects the field gets one automatic retry without it -- several
 *       local runtimes 400 on unknown fields.</li>
 *   <li>HTTP errors are surfaced with the provider's own message body, because the
 *       useful part ("model not found", "insufficient quota") lives there.</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenAiCompatibleClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Executor llmExecutor;

    // Explicit @Autowired: the class has a second constructor for tests, and Spring will
    // not guess between two candidates.
    @Autowired
    public OpenAiCompatibleClient(ObjectMapper objectMapper, LlmProperties properties,
                                  @Qualifier(LlmQueueConfig.LLM_EXECUTOR) Executor llmExecutor) {
        this(objectMapper, defaultRestClient(properties), llmExecutor);
    }

    /** Test seam: lets a test bind MockRestServiceServer to the client's RestClient. */
    OpenAiCompatibleClient(ObjectMapper objectMapper, RestClient restClient, Executor llmExecutor) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.llmExecutor = llmExecutor;
    }

    private static RestClient defaultRestClient(LlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        // Tailoring a whole resume is a long generation; the default read timeout is far
        // too aggressive for it.
        factory.setReadTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds()));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Queues the call on {@code llmExecutor} and blocks the caller until it runs, rather
     * than making the HTTP request on the calling thread directly -- that is what lets a
     * fixed number of concurrent calls (default 1) be enforced no matter how many tailoring
     * runs are asking for one at once. The queue itself is bounded ({@link
     * com.resumetailor.config.LlmQueueProperties#capacity()}); a call made once it is full
     * fails fast with an actionable message instead of growing the backlog forever.
     */
    public LlmChatResult chat(LlmSettings settings, String systemPrompt, String userPrompt, boolean jsonMode) {
        CompletableFuture<LlmChatResult> future;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> chatBlocking(settings, systemPrompt, userPrompt, jsonMode), llmExecutor);
        } catch (RejectedExecutionException ex) {
            throw new LlmException("Too many LLM calls are already queued -- wait for earlier ones to finish, "
                    + "or raise resume-tailor.llm.queue.capacity.", ex);
        }
        try {
            return future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof LlmException llmException) {
                throw llmException;
            }
            throw new LlmException(cause != null ? messageOf(cause) : messageOf(ex), cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for the LLM call queue", ex);
        }
    }

    private static String messageOf(Throwable ex) {
        return ex.getMessage() == null ? ex.toString() : ex.getMessage();
    }

    private LlmChatResult chatBlocking(LlmSettings settings, String systemPrompt, String userPrompt, boolean jsonMode) {
        ResponseEntity<String> response = post(settings, systemPrompt, userPrompt, jsonMode);

        if (jsonMode && response.getStatusCode().isError() && mentionsResponseFormat(response.getBody())) {
            log.info("Endpoint rejected response_format; retrying without JSON mode");
            response = post(settings, systemPrompt, userPrompt, false);
        }
        if (response.getStatusCode().isError()) {
            throw new LlmException(describeError(response));
        }
        return parse(response.getBody());
    }

    private ResponseEntity<String> post(LlmSettings settings, String systemPrompt, String userPrompt,
                                        boolean jsonMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", settings.model());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        payload.put("temperature", settings.temperature());
        payload.put("max_tokens", settings.maxOutputTokens());
        if (jsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        // Serialised up front so the request carries a Content-Length. Streaming a Map
        // produces chunked encoding, which several small OpenAI-compatible servers and
        // proxies cannot read -- they drop the connection instead of answering.
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ex) {
            throw new LlmException("Could not build the request for the LLM endpoint: " + ex.getMessage(), ex);
        }

        try {
            return restClient.post()
                    .uri(chatCompletionsUrl(settings.baseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + settings.apiKey());
                        }
                    })
                    .body(body)
                    .retrieve()
                    // Disable the default throw-on-error so we can read the provider's message body.
                    .onStatus(status -> true, (request, res) -> {
                    })
                    .toEntity(String.class);
        } catch (ResourceAccessException ex) {
            throw new LlmException("Could not reach " + settings.baseUrl()
                    + " -- check the base URL is right and the server is running. (" + ex.getMessage() + ")", ex);
        } catch (RestClientException ex) {
            // The server accepted the connection but the exchange broke part-way, e.g. it
            // reset the connection instead of responding. Still the user's endpoint at fault,
            // so it must surface as an LlmException with a message, never as a bare 500.
            throw new LlmException("The LLM endpoint at " + settings.baseUrl()
                    + " closed the connection without a usable response. (" + ex.getMessage() + ")", ex);
        }
    }

    /** Accepts base URLs with or without a trailing slash, e.g. "https://api.openai.com/v1". */
    static String chatCompletionsUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        return trimmed + "/chat/completions";
    }

    private LlmChatResult parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            if (content.isBlank()) {
                throw new LlmException("The model returned an empty completion. "
                        + "If the model is a reasoning model, raise Max output tokens in Settings.");
            }
            JsonNode usage = root.path("usage");
            return new LlmChatResult(
                    content,
                    root.path("model").asText(null),
                    usage.hasNonNull("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                    usage.hasNonNull("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException("Could not read the response from the LLM endpoint: " + ex.getMessage(), ex);
        }
    }

    private static boolean mentionsResponseFormat(String body) {
        return body != null && body.contains("response_format");
    }

    private String describeError(ResponseEntity<String> response) {
        String detail = extractProviderMessage(response.getBody());
        return "LLM endpoint returned " + response.getStatusCode().value()
                + (detail.isBlank() ? "" : ": " + detail);
    }

    private String extractProviderMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String message = error.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // Not JSON -- fall through and return a truncated raw body.
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
