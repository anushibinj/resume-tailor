package com.resumetailor.llm;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.config.LlmProperties;
import com.resumetailor.llm.LlmDtos.LlmProfileResponse;
import com.resumetailor.llm.LlmDtos.SaveLlmProfileRequest;
import com.resumetailor.llm.LlmDtos.TestConnectionResponse;
import com.resumetailor.user.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProfileService {

    private static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.20");
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 8000;

    private final LlmProfileRepository repository;
    private final CurrentUserProvider currentUser;
    private final ApiKeyCipher cipher;
    private final OpenAiCompatibleClient client;
    private final LlmProperties properties;

    @Transactional(readOnly = true)
    public List<LlmProfileResponse> list() {
        return repository.findAllByOwnerIdOrderByCreatedAtAsc(currentUser.currentUserId())
                .stream()
                .map(LlmProfileService::toResponse)
                .toList();
    }

    @Transactional
    public LlmProfileResponse create(SaveLlmProfileRequest request) {
        UUID ownerId = currentUser.currentUserId();
        String name = request.name().trim();
        if (repository.existsByOwnerIdAndNameIgnoreCase(ownerId, name)) {
            throw new BadRequestException("A profile named '" + name + "' already exists");
        }

        LlmProfile profile = new LlmProfile();
        profile.setOwnerId(ownerId);
        profile.setName(name);
        apply(profile, request);
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            storeKey(profile, request.apiKey().trim());
        }

        boolean makeDefault = Boolean.TRUE.equals(request.makeDefault()) || repository.countByOwnerId(ownerId) == 0;
        if (makeDefault) {
            repository.clearDefault(ownerId);
            profile.setDefault(true);
        }
        return toResponse(repository.save(profile));
    }

    @Transactional
    public LlmProfileResponse update(UUID id, SaveLlmProfileRequest request) {
        LlmProfile profile = require(id);
        String name = request.name().trim();
        if (repository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(profile.getOwnerId(), name, id)) {
            throw new BadRequestException("A profile named '" + name + "' already exists");
        }
        profile.setName(name);
        apply(profile, request);
        // A blank key means "leave the stored one alone", not "clear it".
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            storeKey(profile, request.apiKey().trim());
        }
        if (Boolean.TRUE.equals(request.makeDefault()) && !profile.isDefault()) {
            repository.clearDefault(profile.getOwnerId());
            profile.setDefault(true);
        }
        return toResponse(repository.save(profile));
    }

    @Transactional
    public LlmProfileResponse setDefault(UUID id) {
        LlmProfile profile = require(id);
        repository.clearDefault(profile.getOwnerId());
        profile.setDefault(true);
        return toResponse(repository.save(profile));
    }

    @Transactional
    public void delete(UUID id) {
        LlmProfile profile = require(id);
        boolean wasDefault = profile.isDefault();
        UUID ownerId = profile.getOwnerId();
        repository.delete(profile);
        if (wasDefault) {
            repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        repository.save(next);
                    });
        }
    }

    /** Round-trips a tiny prompt so the user can verify credentials before a real run. */
    @Transactional(readOnly = true)
    public TestConnectionResponse test(UUID id) {
        LlmSettings settings = resolveSettings(id);
        long started = System.currentTimeMillis();
        try {
            LlmChatResult result = client.chat(
                    settings,
                    "You are a connection test. Reply with exactly: OK",
                    "Reply with exactly: OK",
                    false);
            long elapsed = System.currentTimeMillis() - started;
            return new TestConnectionResponse(true, "Connected. Model replied: "
                    + result.content().strip(), result.model(), elapsed);
        } catch (LlmException ex) {
            return new TestConnectionResponse(
                    false, ex.getMessage(), settings.model(), System.currentTimeMillis() - started);
        }
    }

    /** Decrypts the stored key. Never expose the result outside the backend. */
    @Transactional(readOnly = true)
    public LlmSettings resolveSettings(UUID profileId) {
        LlmProfile profile = profileId != null ? require(profileId) : requireDefault();
        return new LlmSettings(
                profile.getBaseUrl(),
                cipher.decrypt(profile.getApiKeyEncrypted()),
                profile.getModel(),
                profile.getTemperature(),
                profile.getMaxOutputTokens());
    }

    @Transactional(readOnly = true)
    public UUID resolveProfileId(UUID profileId) {
        return profileId != null ? require(profileId).getId() : requireDefault().getId();
    }

    private LlmProfile requireDefault() {
        return repository.findByOwnerIdAndIsDefaultTrue(currentUser.currentUserId())
                .orElseThrow(() -> new BadRequestException(
                        "No LLM profile is configured. Add one under Settings first."));
    }

    private LlmProfile require(UUID id) {
        return repository.findByIdAndOwnerId(id, currentUser.currentUserId())
                .orElseThrow(() -> NotFoundException.of("LLM profile", id));
    }

    private void apply(LlmProfile profile, SaveLlmProfileRequest request) {
        profile.setBaseUrl(request.baseUrl().trim());
        profile.setModel(request.model().trim());
        profile.setTemperature(request.temperature() != null ? request.temperature() : DEFAULT_TEMPERATURE);
        profile.setMaxOutputTokens(
                request.maxOutputTokens() != null ? request.maxOutputTokens() : DEFAULT_MAX_OUTPUT_TOKENS);
    }

    private void storeKey(LlmProfile profile, String plaintextKey) {
        profile.setApiKeyEncrypted(cipher.encrypt(plaintextKey));
        profile.setApiKeyHint(ApiKeyCipher.hint(plaintextKey));
    }

    /** Seeds a profile from .env on first start so a fresh clone is usable immediately. */
    @Transactional
    public void seedDefaultProfile() {
        UUID ownerId = currentUser.currentUserId();
        if (repository.countByOwnerId(ownerId) > 0) {
            return;
        }
        String apiKey = properties.defaultApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("No LLM_API_KEY in .env -- add an LLM profile under Settings to start tailoring");
            return;
        }
        LlmProfile profile = new LlmProfile();
        profile.setOwnerId(ownerId);
        profile.setName("Default");
        profile.setBaseUrl(properties.defaultBaseUrl());
        profile.setModel(properties.defaultModel());
        profile.setTemperature(properties.defaultTemperature());
        profile.setMaxOutputTokens(properties.defaultMaxOutputTokens());
        profile.setDefault(true);
        storeKey(profile, apiKey.trim());
        repository.save(profile);
        log.info("Seeded LLM profile 'Default' ({} @ {})", properties.defaultModel(), properties.defaultBaseUrl());
    }

    private static LlmProfileResponse toResponse(LlmProfile profile) {
        boolean hasKey = profile.getApiKeyEncrypted() != null && !profile.getApiKeyEncrypted().isBlank();
        String hint = profile.getApiKeyHint();
        return new LlmProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getBaseUrl(),
                profile.getModel(),
                profile.getTemperature(),
                profile.getMaxOutputTokens(),
                profile.isDefault(),
                hasKey,
                hasKey && hint != null && !hint.isBlank() ? "••••" + hint : "",
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
