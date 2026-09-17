package com.resumetailor.resume;

import com.resumetailor.common.BadRequestException;
import com.resumetailor.common.NotFoundException;
import com.resumetailor.resume.ResumeDtos.ResumeDetail;
import com.resumetailor.resume.ResumeDtos.ResumeSummary;
import com.resumetailor.resume.ResumeDtos.SaveResumeRequest;
import com.resumetailor.resume.ResumeDtos.SectionSummary;
import com.resumetailor.user.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public List<ResumeSummary> list() {
        return resumeRepository.findAllByOwnerIdOrderByCreatedAtDesc(currentUser.currentUserId())
                .stream()
                .map(ResumeService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetail get(UUID id) {
        return toDetail(require(id));
    }

    /** Resolves the resume to tailor from: the explicit id, else the user's default. */
    @Transactional(readOnly = true)
    public Resume requireForTailoring(UUID id) {
        if (id != null) {
            return require(id);
        }
        return resumeRepository.findByOwnerIdAndIsDefaultTrue(currentUser.currentUserId())
                .orElseThrow(() -> new BadRequestException(
                        "No resume selected and no default resume is set. Add one under Resumes first."));
    }

    @Transactional
    public ResumeDetail create(SaveResumeRequest request) {
        UUID ownerId = currentUser.currentUserId();
        String name = request.name().trim();
        if (resumeRepository.existsByOwnerIdAndNameIgnoreCase(ownerId, name)) {
            throw new BadRequestException("A resume named '" + name + "' already exists");
        }

        Resume resume = new Resume();
        resume.setOwnerId(ownerId);
        resume.setName(name);
        resume.applySource(request.sourceText(), resolveFormat(request));

        // The first resume a user adds is their default, so /tailor works without setup.
        boolean makeDefault = Boolean.TRUE.equals(request.makeDefault())
                || resumeRepository.countByOwnerId(ownerId) == 0;
        if (makeDefault) {
            resumeRepository.clearDefault(ownerId);
            resume.setDefault(true);
        }
        return toDetail(resumeRepository.save(resume));
    }

    @Transactional
    public ResumeDetail update(UUID id, SaveResumeRequest request) {
        Resume resume = require(id);
        String name = request.name().trim();
        if (resumeRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(resume.getOwnerId(), name, id)) {
            throw new BadRequestException("A resume named '" + name + "' already exists");
        }
        resume.setName(name);
        resume.applySource(request.sourceText(), resolveFormat(request));
        if (Boolean.TRUE.equals(request.makeDefault()) && !resume.isDefault()) {
            resumeRepository.clearDefault(resume.getOwnerId());
            resume.setDefault(true);
        }
        return toDetail(resumeRepository.save(resume));
    }

    @Transactional
    public ResumeDetail setDefault(UUID id) {
        Resume resume = require(id);
        resumeRepository.clearDefault(resume.getOwnerId());
        resume.setDefault(true);
        return toDetail(resumeRepository.save(resume));
    }

    @Transactional
    public void delete(UUID id) {
        Resume resume = require(id);
        boolean wasDefault = resume.isDefault();
        UUID ownerId = resume.getOwnerId();
        resumeRepository.delete(resume);
        if (wasDefault) {
            // Promote the most recent survivor so the user is never left without a default.
            resumeRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        resumeRepository.save(next);
                    });
        }
    }

    private Resume require(UUID id) {
        return resumeRepository.findByIdAndOwnerId(id, currentUser.currentUserId())
                .orElseThrow(() -> NotFoundException.of("Resume", id));
    }

    private static ResumeFormat resolveFormat(SaveResumeRequest request) {
        return request.format() != null ? request.format() : ResumeParser.detectFormat(request.sourceText());
    }

    private static ResumeSummary toSummary(Resume resume) {
        return new ResumeSummary(
                resume.getId(),
                resume.getName(),
                resume.getFormat(),
                resume.isDefault(),
                SectionSegmenter.segment(resume.getBodyText(), resume.getFormat()).size(),
                resume.getSourceText().length(),
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }

    private static ResumeDetail toDetail(Resume resume) {
        List<SectionSummary> sections =
                SectionSegmenter.segment(resume.getBodyText(), resume.getFormat()).stream()
                        .map(section -> new SectionSummary(
                                section.displayTitle(), section.level(), section.content().length()))
                        .toList();
        return new ResumeDetail(
                resume.getId(),
                resume.getName(),
                resume.getFormat(),
                resume.isDefault(),
                resume.getSourceText(),
                resume.getPreamble(),
                resume.getBodyText(),
                resume.getDocumentTail(),
                sections,
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }
}
