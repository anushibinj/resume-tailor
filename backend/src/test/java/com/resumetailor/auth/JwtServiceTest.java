package com.resumetailor.auth;

import com.resumetailor.config.JwtProperties;
import com.resumetailor.user.Role;
import com.resumetailor.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-bytes-long-for-hs256";

    private static User user() {
        User user = new User("jane@example.com", "Jane Doe", "google-sub-1", null, Role.NORMAL_USER);
        user.setId(java.util.UUID.randomUUID());
        return user;
    }

    @Test
    void aTokenItIssuesParsesBackToTheSameUserAndRole() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        User user = user();

        String token = service.issue(user);
        JwtService.AuthenticatedPrincipal principal = service.parse(token);

        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo("jane@example.com");
        assertThat(principal.role()).isEqualTo(Role.NORMAL_USER);
    }

    @Test
    void aTamperedTokenFailsToParseInsteadOfThrowing() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        String token = service.issue(user());

        assertThat(service.parse(token.substring(0, token.length() - 2) + "xx")).isNull();
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));

        assertThat(service.parse("not-a-jwt")).isNull();
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("", 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesToStartWithATooShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
