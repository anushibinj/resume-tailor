package com.resumetailor.config;

import com.resumetailor.llm.LlmProfileService;
import com.resumetailor.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Makes a fresh clone usable on first boot: creates the single v1 user, then seeds an
 * LLM profile from .env when a key is present.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSeeder implements ApplicationRunner {

    private final UserService userService;
    private final LlmProfileService llmProfileService;

    @Override
    public void run(ApplicationArguments args) {
        userService.ensureDefaultUser();
        llmProfileService.seedDefaultProfile();
    }
}
