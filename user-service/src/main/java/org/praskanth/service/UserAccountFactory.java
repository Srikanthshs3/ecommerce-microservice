package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import org.praskanth.entity.Role;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.util.Emails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** The one place that builds a login identity, so customer and seller sign-up cannot drift apart. */
@Component
@RequiredArgsConstructor
public class UserAccountFactory {

    private final RoleRepo roleRepo;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    /** Fails fast (409) on duplicate email/phone; the DB unique constraints remain the final guard. */
    public void assertIdentityAvailable(String email, String phoneNumber) {
        if (userRepo.existsByEmail(Emails.normalize(email))) {
            throw new ConflictException("An account with this email already exists");
        }
        String phone = blankToNull(phoneNumber);
        if (phone != null && userRepo.existsByPhoneNumber(phone)) {
            throw new ConflictException("An account with this phone number already exists");
        }
    }

    public User newUser(String email, String name, String phoneNumber, String rawPassword, UserRole role) {
        Role dbRole = roleRepo.findByName(role)
                .orElseThrow(() -> new BadRequestException("Role " + role + " is not configured in the system"));
        Set<Role> roles = new HashSet<>();
        roles.add(dbRole); // mutable on purpose: a customer may later be granted SELLER

        return User.builder()
                .email(Emails.normalize(email))
                .name(name == null ? null : name.trim())
                .phoneNumber(blankToNull(phoneNumber))
                .password(passwordEncoder.encode(rawPassword))
                .role(roles)
                .emailVerified(true) // only reachable after the OTP flow
                .build();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
