package com.resumetailor.auth;

import com.resumetailor.user.Role;
import com.resumetailor.user.User;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record GoogleLoginRequest(@NotBlank String idToken) {
    }

    public record UserResponse(UUID id, String email, String displayName, String pictureUrl, Role role) {
        public static UserResponse of(User user) {
            return new UserResponse(
                    user.getId(), user.getEmail(), user.getDisplayName(), user.getPictureUrl(), user.getRole());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
