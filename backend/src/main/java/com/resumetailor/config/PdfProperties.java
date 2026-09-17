package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the containerised TeX compiler.
 *
 * @param enabled               false disables PDF export entirely; source download still works
 * @param dockerBinary          path to the docker CLI
 * @param image                 image tag built from docker/tex/Dockerfile
 * @param compileTimeoutSeconds hard wall-clock cap; a runaway LaTeX macro must not hang the server
 * @param memoryLimit           passed to docker --memory
 * @param cpuLimit              passed to docker --cpus
 * @param artifactPath          directory where compiled PDFs are kept
 */
@ConfigurationProperties(prefix = "resume-tailor.pdf")
public record PdfProperties(
        boolean enabled,
        String dockerBinary,
        String image,
        int compileTimeoutSeconds,
        String memoryLimit,
        String cpuLimit,
        String artifactPath) {
}
