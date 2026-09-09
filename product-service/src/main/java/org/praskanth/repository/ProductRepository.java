package org.praskanth.repository;

import org.praskanth.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, Long> {

    //Optional<Product> findByProductId(Long productId);

    Optional<Product> findById(String id);

    List<Product> findByCategoryId(String categoryId);

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByNameContainingOrDescriptionContaining(String name, String description);
}
