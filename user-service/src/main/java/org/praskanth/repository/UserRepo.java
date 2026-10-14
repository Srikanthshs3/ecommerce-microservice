package org.praskanth.repository;

import io.micrometer.observation.annotation.Observed;
import org.praskanth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Observed
public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
