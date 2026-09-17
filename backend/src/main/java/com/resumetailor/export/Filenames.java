package com.resumetailor.export;

import java.util.Locale;

public final class Filenames {

    private Filenames() {
    }

    /**
     * Builds a download name like {@code resume-acme-backend-engineer.tex}.
     * Everything outside [a-z0-9-] is dropped so the name is safe in a
     * Content-Disposition header on any platform.
     */
    public static String forRun(String company, String role, String extension) {
        StringBuilder name = new StringBuilder("resume");
        appendSlug(name, company);
        appendSlug(name, role);
        return name + "." + extension;
    }

    private static void appendSlug(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!slug.isBlank()) {
            target.append('-').append(slug.length() > 40 ? slug.substring(0, 40) : slug);
        }
    }
}
