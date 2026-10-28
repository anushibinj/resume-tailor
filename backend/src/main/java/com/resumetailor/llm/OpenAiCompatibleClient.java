package com.resumetailor.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to any endpoint exposing OpenAI's {@code POST /chat/completions}.
 *
 * <p>Two compatibility behaviours are deliberate:
 * <ul>
 *   <li>{@code response_format: json_object} is requested when we need JSON, but a
 *       server that rejects the field gets one automatic retry without it -- several
 *       local runtimes 400 on unknown fields.</li>
 *   <li>The output limit is sent as {@code max_tokens}, falling back to
 *       {@code max_completion_tokens} (and remembered) for models that reject it.</li>
 *   <li>HTTP errors are surfaced with the provider's own message body, because the
 *       useful part ("model not found", "insufficient quota") lives there.</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenAiCompatibleClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    private static final String MAX_TOKENS = "max_tokens";
    private static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    /** Which output-limit field each endpoint+model accepted, learned from earlier calls. */
    private final Map<String, String> tokenParamByEndpoint = new ConcurrentHashMap<>();

    // Explicit @Autowired: the class has a second constructor for tests, and Spring will
    // not guess between two candidates.
    @Autowired
    public OpenAiCompatibleClient(ObjectMapper objectMapper, LlmProperties properties) {
        this(objectMapper, defaultRestClient(properties));
    }

    /** Test seam: lets a test bind MockRestServiceServer to the client's RestClient. */
    OpenAiCompatibleClient(ObjectMapper objectMapper, RestClient restClient) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient defaultRestClient(LlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        // Tailoring a whole resume is a long generation; the default read timeout is far
        // too aggressive for it.
        factory.setReadTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds()));
        return RestClient.builder().requestFactory(factory).build();
    }

    public LlmChatResult chat(LlmSettings settings, String systemPrompt, String userPrompt, boolean jsonMode) {
        String endpointKey = settings.baseUrl() + "|" + settings.model();
        String tokenParam = tokenParamByEndpoint.getOrDefault(endpointKey, MAX_TOKENS);
        long started = System.nanoTime();
        int attempts = 1;
        log.info("LLM call starting: model={} jsonMode={} promptChars={} maxOutputTokens={} endpoint={}",
                settings.model(), jsonMode, systemPrompt.length() + userPrompt.length(),
                settings.maxOutputTokens(), settings.baseUrl());
        ResponseEntity<String> response = post(settings, systemPrompt, userPrompt, jsonMode, tokenParam);

        // OpenAI's newer models (o-series, gpt-5...) reject max_tokens and want
        // max_completion_tokens; older and third-party servers only know max_tokens. Try one,
        // then the other, and remember what worked so later calls get it right first time.
        if (response.getStatusCode().isError()) {
            String other = otherTokenParam(tokenParam, response.getBody());
            if (other != null) {
                log.warn("Endpoint rejected {} (HTTP {}); retrying with {} -- this repeats the whole call",
                        tokenParam, response.getStatusCode().value(), other);
                tokenParam = other;
                attempts++;
                response = post(settings, systemPrompt, userPrompt, jsonMode, tokenParam);
                if (!response.getStatusCode().isError()) {
                    tokenParamByEndpoint.put(endpointKey, tokenParam);
                }
            }
        }

        if (jsonMode && response.getStatusCode().isError() && mentionsResponseFormat(response.getBody())) {
            log.warn("Endpoint rejected response_format (HTTP {}); retrying without JSON mode -- this repeats the whole call",
                    response.getStatusCode().value());
            attempts++;
            response = post(settings, systemPrompt, userPrompt, false, tokenParam);
        }
        if (response.getStatusCode().isError()) {
            log.warn("LLM call failed after {} attempt(s) in {} ms: HTTP {}",
                    attempts, elapsedMs(started), response.getStatusCode().value());
            throw new LlmException(describeError(response));
        }
        LlmChatResult result = parse(response.getBody());
        log.info("LLM call finished: model={} attempts={} elapsed={} ms promptTokens={} completionTokens={}",
                result.model(), attempts, elapsedMs(started), result.promptTokens(), result.completionTokens());
        return result;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private ResponseEntity<String> post(LlmSettings settings, String systemPrompt, String userPrompt,
                                        boolean jsonMode, String tokenParam) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", settings.model());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        payload.put("temperature", settings.temperature());
        payload.put(tokenParam, settings.maxOutputTokens());
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

        long sent = System.nanoTime();
        log.debug("POST {} ({} bytes, {}={}, jsonMode={})",
                chatCompletionsUrl(settings.baseUrl()), body.length, tokenParam, settings.maxOutputTokens(), jsonMode);
        try {
            ResponseEntity<String> response = restClient.post()
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
            log.debug("HTTP {} after {} ms ({} response chars)", response.getStatusCode().value(),
                    elapsedMs(sent), response.getBody() == null ? 0 : response.getBody().length());
            return response;
        } catch (ResourceAccessException ex) {
            log.warn("No response from {} after {} ms: {}", settings.baseUrl(), elapsedMs(sent), ex.getMessage());
            throw new LlmException("Could not reach " + settings.baseUrl()
                    + " -- check the base URL is right and the server is running. (" + ex.getMessage() + ")", ex);
        } catch (RestClientException ex) {
            log.warn("Exchange with {} broke after {} ms: {}", settings.baseUrl(), elapsedMs(sent), ex.getMessage());
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

    /**
     * If the error says the parameter we sent is unsupported and names the other one,
     * returns the other; otherwise null (the failure is something else).
     */
    private static String otherTokenParam(String sent, String body) {
        if (body == null) {
            return null;
        }
        if (MAX_TOKENS.equals(sent)) {
            return body.contains(MAX_COMPLETION_TOKENS) ? MAX_COMPLETION_TOKENS : null;
        }
        // "max_tokens" is not a substring of "max_completion_tokens", so this is unambiguous.
        return body.contains(MAX_TOKENS) ? MAX_TOKENS : null;
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
