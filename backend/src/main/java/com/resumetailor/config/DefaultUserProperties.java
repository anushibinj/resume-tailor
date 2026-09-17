package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Identity of the single v1 user that owns every record. See CLAUDE.md for the v2 plan. */
@ConfigurationProperties(prefix = "resume-tailor.user")
public record DefaultUserProperties(String defaultEmail, String defaultDisplayName) {
}
