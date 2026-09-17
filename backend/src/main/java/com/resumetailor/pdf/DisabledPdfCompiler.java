package com.resumetailor.pdf;

import com.resumetailor.common.ServiceUnavailableException;
import com.resumetailor.resume.ResumeFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Active when PDF_ENABLED=false. Source download still works; only compiling is off. */
@Component
@ConditionalOnProperty(prefix = "resume-tailor.pdf", name = "enabled", havingValue = "false")
public class DisabledPdfCompiler implements PdfCompiler {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public PdfCompileResult compile(String source, ResumeFormat format) {
        throw new ServiceUnavailableException(
                "PDF compilation is disabled. Set PDF_ENABLED=true in backend/.env to turn it on.");
    }
}
