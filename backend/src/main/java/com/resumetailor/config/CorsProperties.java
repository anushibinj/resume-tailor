package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "resume-tailor.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
