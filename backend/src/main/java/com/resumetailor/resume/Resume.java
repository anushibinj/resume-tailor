package com.resumetailor.resume;

import com.resumetailor.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A base resume the user tailors from.
 *
 * <p>{@code preamble} / {@code bodyText} / {@code documentTail} are the output of
 * {@link ResumeParser} and are stored split so the tailoring pipeline never has to
 * re-derive them -- and never has to send the preamble to a model.
 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
public class Resume extends AuditedEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private ResumeFormat format;

    @Column(name = "source_text", nullable = false, columnDefinition = "text")
    private String sourceText;

    @Column(name = "preamble", nullable = false, columnDefinition = "text")
    private String preamble;

    @Column(name = "body_text", nullable = false, columnDefinition = "text")
    private String bodyText;

    @Column(name = "document_tail", nullable = false, columnDefinition = "text")
    private String documentTail;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    /** Applies a new source text, re-splitting it into preamble / body / tail. */
    public void applySource(String source, ResumeFormat detectedFormat) {
        ParsedResume parsed = ResumeParser.parse(source, detectedFormat);
        this.sourceText = source;
        this.format = parsed.format();
        this.preamble = parsed.preamble();
        this.bodyText = parsed.body();
        this.documentTail = parsed.documentTail();
    }

    public ParsedResume toParsed() {
        return new ParsedResume(format, preamble, bodyText, documentTail);
    }
}
