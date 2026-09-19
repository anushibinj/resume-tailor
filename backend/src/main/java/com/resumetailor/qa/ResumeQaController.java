package com.resumetailor.qa;

import com.resumetailor.qa.ResumeQaDtos.AskQuestionRequest;
import com.resumetailor.qa.ResumeQaDtos.ResumeQuestionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/resumes/{resumeId}/questions")
@RequiredArgsConstructor
public class ResumeQaController {

    private final ResumeQaService resumeQaService;

    /** Synchronous: a single question is answered in this one round trip. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeQuestionResponse ask(@PathVariable UUID resumeId, @Valid @RequestBody AskQuestionRequest request) {
        return resumeQaService.ask(resumeId, request);
    }

    @GetMapping
    public Page<ResumeQuestionResponse> history(@PathVariable UUID resumeId,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return resumeQaService.history(resumeId, pageable);
    }
}
