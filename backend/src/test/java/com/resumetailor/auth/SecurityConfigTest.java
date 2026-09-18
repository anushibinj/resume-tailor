package com.resumetailor.auth;

import com.resumetailor.user.Role;
import com.resumetailor.user.User;
import com.resumetailor.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@code SecurityFilterChain} end to end -- unlike the
 * {@code @WebMvcTest} controller tests, which run with {@code addFilters = false} and
 * therefore prove nothing about auth itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "resume-tailor.security.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "resume-tailor.llm.default-api-key=",
        "resume-tailor.google.client-id=test-client-id.apps.googleusercontent.com",
        "resume-tailor.jwt.secret=test-jwt-secret-at-least-32-bytes-long-for-hs256",
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aProtectedEndpointWithNoTokenIs401WithTheSharedErrorShape() throws Exception {
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void aProtectedEndpointWithAValidTokenIsReachable() throws Exception {
        User user = userRepository.save(new User(
                "reachable@example.com", "Reachable User", "google-sub-reachable", null, Role.NORMAL_USER));
        String token = jwtService.issue(user);

        mockMvc.perform(get("/api/resumes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aBogusTokenIsTreatedAsUnauthenticatedRatherThanCrashing() throws Exception {
        mockMvc.perform(get("/api/resumes").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theGoogleSignInEndpointIsReachableWithoutAToken() throws Exception {
        // permitAll on /api/auth/**: a malformed idToken still proves the request reached
        // the controller (400 from GoogleTokenVerifier) instead of being blocked at 401/403.
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"not-a-real-google-token\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meWithNoTokenIs401NotA500() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void meWithAValidTokenReturnsTheProfileSoASessionSurvivesAReload() throws Exception {
        User user = userRepository.save(new User(
                "restore@example.com", "Restore User", "google-sub-restore", null, Role.NORMAL_USER));
        String token = jwtService.issue(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("restore@example.com"));
    }

    @Test
    void actuatorHealthIsReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
