package org.praskanth.repository;

import org.praskanth.modal.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart,Long> {

    Optional<Cart> findByUserEmail(String email);

    Optional<Cart> findByUserId(Long userId);

}
