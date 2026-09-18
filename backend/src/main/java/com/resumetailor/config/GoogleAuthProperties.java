package com.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param clientId the OAuth 2.0 Web client ID from Google Cloud Console
 *                 (APIs &amp; Services &gt; Credentials). Required -- it doubles as the
 *                 audience every Google ID token is checked against, so a wrong or
 *                 missing value means every sign-in fails closed rather than accepting
 *                 a token meant for a different app.
 */
@ConfigurationProperties(prefix = "resume-tailor.google")
public record GoogleAuthProperties(String clientId) {
}
