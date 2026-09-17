package com.resumetailor.resume;

import com.resumetailor.resume.ResumeDtos.ResumeDetail;
import com.resumetailor.resume.ResumeDtos.ResumeSummary;
import com.resumetailor.resume.ResumeDtos.SaveResumeRequest;
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
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping
    public List<ResumeSummary> list() {
        return resumeService.list();
    }

    @GetMapping("/{id}")
    public ResumeDetail get(@PathVariable UUID id) {
        return resumeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDetail create(@Valid @RequestBody SaveResumeRequest request) {
        return resumeService.create(request);
    }

    @PutMapping("/{id}")
    public ResumeDetail update(@PathVariable UUID id, @Valid @RequestBody SaveResumeRequest request) {
        return resumeService.update(id, request);
    }

    @PostMapping("/{id}/default")
    public ResumeDetail setDefault(@PathVariable UUID id) {
        return resumeService.setDefault(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        resumeService.delete(id);
    }
}
