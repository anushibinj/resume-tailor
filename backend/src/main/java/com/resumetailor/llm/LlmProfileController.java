package com.resumetailor.llm;

import com.resumetailor.llm.LlmDtos.LlmProfileResponse;
import com.resumetailor.llm.LlmDtos.SaveLlmProfileRequest;
import com.resumetailor.llm.LlmDtos.TestConnectionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/llm-profiles")
@RequiredArgsConstructor
public class LlmProfileController {

    private final LlmProfileService service;

    @GetMapping
    public List<LlmProfileResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LlmProfileResponse create(@Valid @RequestBody SaveLlmProfileRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public LlmProfileResponse update(@PathVariable UUID id, @Valid @RequestBody SaveLlmProfileRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/default")
    public LlmProfileResponse setDefault(@PathVariable UUID id) {
        return service.setDefault(id);
    }

    /** Returns 200 with {@code ok:false} on a failed connection -- this is a diagnostic, not an error. */
    @PostMapping("/{id}/test")
    public TestConnectionResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
