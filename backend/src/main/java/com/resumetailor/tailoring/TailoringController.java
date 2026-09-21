package com.resumetailor.tailoring;

import com.resumetailor.tailoring.TailoringDtos.CreateRunRequest;
import com.resumetailor.tailoring.TailoringDtos.RunApplicationResponse;
import com.resumetailor.tailoring.TailoringDtos.RunDetail;
import com.resumetailor.tailoring.TailoringDtos.RunSummary;
import com.resumetailor.tailoring.TailoringDtos.SelectSummaryRequest;
import com.resumetailor.tailoring.TailoringDtos.UpdateAppliedRequest;
import com.resumetailor.tailoring.TailoringDtos.UpdateApplicationLinkRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** {@code applied} filters the list when given; omitted shows every run. */
    @GetMapping
    public Page<RunSummary> list(@RequestParam(required = false) Boolean applied,
                                  @PageableDefault(size = 20) Pageable pageable) {
        return tailoringService.listRuns(applied, pageable);
    }

    @GetMapping("/{id}")
    public RunDetail get(@PathVariable UUID id) {
        return tailoringService.getRun(id);
    }

    /** Recorded by the user, not inferred -- see {@link TailoringService#setApplied}. */
    @PatchMapping("/{id}/applied")
    public RunApplicationResponse setApplied(@PathVariable UUID id, @Valid @RequestBody UpdateAppliedRequest request) {
        return tailoringService.setApplied(id, request.applied());
    }

    /** The posting or application-tracker URL for this run; a blank body clears it. */
    @PutMapping("/{id}/application-link")
    public RunApplicationResponse setApplicationLink(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateApplicationLinkRequest request) {
        return tailoringService.setApplicationLink(id, request.applicationLink());
    }

    @PatchMapping("/{id}/suggestions/{suggestionId}")
    public RunDetail updateSuggestion(@PathVariable UUID id,
                                      @PathVariable UUID suggestionId,
                                      @Valid @RequestBody UpdateSuggestionRequest request) {
        return tailoringService.updateSuggestion(id, suggestionId, request.status());
    }

    /** Re-runs the requirement check on its own, keeping additions already accepted. */
    @PostMapping("/{id}/gaps")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunDetail recheckGaps(@PathVariable UUID id) {
        return tailoringService.requestGapRecheck(id);
    }

    /** Picks one of the summary lengths already written; instant, no model call. */
    @PutMapping("/{id}/summary")
    public RunDetail selectSummary(@PathVariable UUID id, @Valid @RequestBody SelectSummaryRequest request) {
        return tailoringService.selectSummaryLines(id, request.lines());
    }

    /** Writes the summary lengths (again): for runs that predate them, or after a failure. */
    @PostMapping("/{id}/summary")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunDetail generateSummary(@PathVariable UUID id) {
        return tailoringService.requestSummary(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        tailoringService.deleteRun(id);
    }
}
