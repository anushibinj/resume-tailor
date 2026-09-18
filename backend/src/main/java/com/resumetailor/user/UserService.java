package com.resumetailor.user;

import com.resumetailor.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** The outcome of a Google sign-in: the resolved user, and whether this was their first. */
    public record UpsertResult(User user, boolean isNew) {
    }

    /**
     * Looks a signed-in Google account up by its stable {@code sub}, falling back to
     * linking an existing row by email (covers an account created before this column
     * existed), and otherwise creates a brand-new {@link Role#NORMAL_USER}.
     */
    @Transactional
    public UpsertResult findOrCreateFromGoogle(GoogleUserInfo info) {
        return userRepository.findByGoogleSub(info.googleSub())
                .map(user -> new UpsertResult(user, false))
                .orElseGet(() -> userRepository.findByEmail(info.email())
                        .map(user -> new UpsertResult(link(user, info), false))
                        .orElseGet(() -> new UpsertResult(create(info), true)));
    }

    @Transactional(readOnly = true)
    public User require(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private User link(User user, GoogleUserInfo info) {
        user.setGoogleSub(info.googleSub());
        if (user.getPictureUrl() == null) {
            user.setPictureUrl(info.pictureUrl());
        }
        return userRepository.save(user);
    }

    private User create(GoogleUserInfo info) {
        log.info("Creating user {} from Google sign-in", info.email());
        return userRepository.save(
                new User(info.email(), info.displayName(), info.googleSub(), info.pictureUrl(), Role.NORMAL_USER));
    }
}
