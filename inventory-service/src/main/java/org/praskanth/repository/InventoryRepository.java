package org.praskanth.repository;

import org.praskanth.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(String productId);
    boolean existsByProductId(String productId);

    /** Several stock keys at once (a product page asks for all variants of one product). */
    List<Inventory> findByProductIdIn(Collection<String> productIds);
}
