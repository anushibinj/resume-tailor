package com.resumetailor.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.resumetailor.common.BadRequestException;
import com.resumetailor.config.GoogleAuthProperties;
import com.resumetailor.user.GoogleUserInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies a Google Sign-In ID token against Google's own published keys: signature,
 * issuer is Google, and audience matches {@code GOOGLE_CLIENT_ID}. Nothing in the token
 * -- email, name, the "sub" identifying the account -- is trusted until all three pass.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(GoogleAuthProperties properties) {
        if (properties.clientId() == null || properties.clientId().isBlank()) {
            throw new IllegalStateException("""
                    GOOGLE_CLIENT_ID is not set. It is the OAuth 2.0 Web client ID from
                    Google Cloud Console (APIs & Services > Credentials), required so
                    Google Sign-In tokens can be verified as issued for this app.""");
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(properties.clientId().trim()))
                .build();
    }

    public GoogleUserInfo verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            throw new BadRequestException("Invalid Google sign-in token");
        }
        if (idToken == null) {
            throw new BadRequestException("Invalid Google sign-in token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadRequestException("Google account email is not verified");
        }

        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        return new GoogleUserInfo(
                payload.getSubject(),
                payload.getEmail(),
                name != null && !name.isBlank() ? name : payload.getEmail(),
                picture);
    }
}
