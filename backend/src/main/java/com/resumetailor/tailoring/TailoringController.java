package com.resumetailor.tailoring;

import com.resumetailor.tailoring.TailoringDtos.CreateRunRequest;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringDtos.RunSummary;
import com.resumetailor.tailoring.TailoringDtos.UpdateSuggestionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class TailoringController {

    private final TailoringService tailoringService;

    /**
     * Returns immediately with a PENDING run. The frontend polls {@link #get(UUID)}
     * until the status is terminal -- tailoring takes tens of seconds and should not
     * hold an HTTP connection open.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunDetail create(@Valid @RequestBody CreateRunRequest request) {
        return tailoringService.createRun(request);
    }

    @GetMapping
    public Page<RunSummary> list(@PageableDefault(size = 20) Pageable pageable) {
        return tailoringService.listRuns(pageable);
    }

    @GetMapping("/{id}")
    public RunDetail get(@PathVariable UUID id) {
        return tailoringService.getRun(id);
    }

    @PatchMapping("/{id}/suggestions/{suggestionId}")
    public RunDetail updateSuggestion(@PathVariable UUID id,
                                      @PathVariable UUID suggestionId,
                                      @Valid @RequestBody UpdateSuggestionRequest request) {
        return tailoringService.updateSuggestion(id, suggestionId, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        tailoringService.deleteRun(id);
    }
}
