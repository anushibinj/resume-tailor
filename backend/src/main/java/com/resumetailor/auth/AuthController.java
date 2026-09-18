package com.resumetailor.auth;

import com.resumetailor.auth.AuthDtos.AuthResponse;
import com.resumetailor.auth.AuthDtos.GoogleLoginRequest;
import com.resumetailor.auth.AuthDtos.UserResponse;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.user.CurrentUserProvider;
import com.resumetailor.user.GoogleUserInfo;
import com.resumetailor.user.User;
import com.resumetailor.user.UserService;
import com.resumetailor.user.UserService.UpsertResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only unauthenticated door into the API (see {@code SecurityConfig}'s permitAll on
 * {@code /api/auth/**}). {@code POST /google} takes the ID token Google's Sign-In JS
 * issued in the browser, verifies it server-side, and exchanges it for the app's own
 * session token so the backend never has to re-verify a Google token per request.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserService userService;
    private final LlmProfileService llmProfileService;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUser;

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleUserInfo info = googleTokenVerifier.verify(request.idToken());
        UpsertResult result = userService.findOrCreateFromGoogle(info);
        if (result.isNew()) {
            llmProfileService.seedDefaultProfile(result.user().getId());
        }
        return new AuthResponse(jwtService.issue(result.user()), UserResponse.of(result.user()));
    }

    /** Lets the frontend restore a session (name, role, avatar) from a stored token on reload. */
    @GetMapping("/me")
    public UserResponse me() {
        User user = userService.require(currentUser.currentUserId());
        return UserResponse.of(user);
    }
}
