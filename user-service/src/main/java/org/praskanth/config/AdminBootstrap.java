package org.praskanth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.Role;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Creates the first ADMIN account on startup so you never have to insert one by hand.
 *
 * The credentials are NOT in the repository: they come from BOOTSTRAP_ADMIN_EMAIL and
 * BOOTSTRAP_ADMIN_PASSWORD in your local .env (which is git-ignored). If either is missing nothing
 * happens, so a fresh clone from GitHub has no admin and no default password.
 *
 * Safe to run on every start: if the email already exists it only makes sure the ADMIN role is present.
 * It never overwrites an existing password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
    private String email;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String password;

    @Value("${BOOTSTRAP_ADMIN_NAME:Admin}")
    private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("Admin bootstrap skipped (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD not set)");
            return;
        }
        String normalizedEmail = email.strip().toLowerCase();
        if (password.length() < 8 || password.length() > 64) {
            log.warn("Admin bootstrap skipped: BOOTSTRAP_ADMIN_PASSWORD must be 8-64 characters");
            return;
        }

        Role adminRole = roleRepo.findByName(UserRole.ADMIN)
                .orElseGet(() -> roleRepo.save(Role.builder().name(UserRole.ADMIN).build()));

        User existing = userRepo.findByEmail(normalizedEmail).orElse(null);
        if (existing != null) {
            if (!existing.hasRole(UserRole.ADMIN)) {
                existing.getRole().add(adminRole);
                userRepo.save(existing);
                log.info("Existing user {} promoted to ADMIN", normalizedEmail);
            } else {
                log.info("Admin {} already exists - nothing to do", normalizedEmail);
            }
            return;
        }

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        User admin = User.builder()
                .email(normalizedEmail)
                .name(name)
                .password(passwordEncoder.encode(password))
                .emailVerified(true)
                .role(roles)
                .build();
        userRepo.save(admin);
        log.info("Bootstrap admin {} created", normalizedEmail);
    }
}
