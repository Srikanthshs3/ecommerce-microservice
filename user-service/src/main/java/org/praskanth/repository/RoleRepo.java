package org.praskanth.repository;

import org.praskanth.entity.Role;
import org.praskanth.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, Long> {
    Optional<Role> findByName(UserRole name);
}
