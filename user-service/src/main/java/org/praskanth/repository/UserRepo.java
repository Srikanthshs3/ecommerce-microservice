package org.praskanth.repository;

import io.micrometer.observation.annotation.Observed;
import org.praskanth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Observed
public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Soft delete written as one explicit UPDATE (same effect as the @SQLDelete on User): the row is hidden
     * (deleted_at), locked, and its personal data is wiped; the e-mail becomes reusable. Returns the number of rows
     * changed (1 = done, 0 = no such active user). Doing it as a plain statement means it always runs inside the
     * current transaction and never depends on what state an entity object happens to be in.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP(6), account_non_locked = false, password = '', "
            + "name = NULL, phone_number = NULL, email = CONCAT('deleted-', id, '@deleted.invalid') "
            + "WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
    int softDelete(@Param("id") Long id);
}
