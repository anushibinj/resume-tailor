package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param charsPerLine how many visible characters one line of the rendered summary holds. It
 *                     is what "N lines" means to the model, so it should match the template's
 *                     text width; the real line count can only be known by compiling the PDF.
 */
@ConfigurationProperties(prefix = "resume-tailor.summary")
public record SummaryProperties(int charsPerLine) {
}
