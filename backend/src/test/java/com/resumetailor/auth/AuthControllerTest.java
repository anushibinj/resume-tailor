package com.resumetailor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.auth.AuthDtos.GoogleLoginRequest;
import com.resumetailor.common.BadRequestException;
import com.resumetailor.config.CorsProperties;
import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.user.CurrentUserProvider;
import com.resumetailor.user.GoogleUserInfo;
import com.resumetailor.user.Role;
import com.resumetailor.user.User;
import com.resumetailor.user.UserService;
import com.resumetailor.user.UserService.UpsertResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the {@code /api/auth} HTTP contract with the Google/JWT machinery mocked out.
 * {@code addFilters = false}: see the note on {@code ResumeControllerTest} -- the real
 * permitAll/authenticated wiring is exercised end-to-end by {@code SecurityConfigTest}.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private LlmProfileService llmProfileService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private static User user(UUID id) {
        User user = new User("jane@example.com", "Jane Doe", "google-sub-1", "https://example.com/pic.jpg",
                Role.NORMAL_USER);
        user.setId(id);
        return user;
    }

    @Test
    void aFirstTimeSignInCreatesTheUserSeedsAProfileAndReturnsAToken() throws Exception {
        UUID id = UUID.randomUUID();
        User user = user(id);
        given(googleTokenVerifier.verify("valid-id-token"))
                .willReturn(new GoogleUserInfo("google-sub-1", "jane@example.com", "Jane Doe", null));
        given(userService.findOrCreateFromGoogle(any())).willReturn(new UpsertResult(user, true));
        given(jwtService.issue(user)).willReturn("app-jwt-token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("valid-id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("app-jwt-token"))
                .andExpect(jsonPath("$.user.email").value("jane@example.com"))
                .andExpect(jsonPath("$.user.role").value("NORMAL_USER"));

        org.mockito.Mockito.verify(llmProfileService).seedDefaultProfile(id);
    }

    @Test
    void aReturningUserIsNotReseeded() throws Exception {
        UUID id = UUID.randomUUID();
        User user = user(id);
        given(googleTokenVerifier.verify(any()))
                .willReturn(new GoogleUserInfo("google-sub-1", "jane@example.com", "Jane Doe", null));
        given(userService.findOrCreateFromGoogle(any())).willReturn(new UpsertResult(user, false));
        given(jwtService.issue(user)).willReturn("app-jwt-token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("valid-id-token"))))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(llmProfileService, org.mockito.Mockito.never()).seedDefaultProfile(any());
    }

    @Test
    void anInvalidGoogleTokenIs400() throws Exception {
        willThrow(new BadRequestException("Invalid Google sign-in token"))
                .given(googleTokenVerifier).verify(eq("bad-token"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequest("bad-token"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Google sign-in token"));
    }

    @Test
    void meReturnsTheCurrentUsersProfile() throws Exception {
        UUID id = UUID.randomUUID();
        given(currentUserProvider.currentUserId()).willReturn(id);
        given(userService.require(id)).willReturn(user(id));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("NORMAL_USER"));
    }
}
