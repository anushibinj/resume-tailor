package com.resumetailor.user;

import com.resumetailor.config.DefaultUserProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DefaultUserProperties properties;

    /** Creates the single v1 user on first start; idempotent afterwards. */
    @Transactional
    public User ensureDefaultUser() {
        return userRepository.findByEmail(properties.defaultEmail())
                .orElseGet(() -> {
                    log.info("Seeding default user {}", properties.defaultEmail());
                    return userRepository.save(
                            new User(properties.defaultEmail(), properties.defaultDisplayName()));
                });
    }
}
