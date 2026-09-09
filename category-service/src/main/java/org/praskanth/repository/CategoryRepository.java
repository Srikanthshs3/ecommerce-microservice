package org.praskanth.repository;

import org.praskanth.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {
    List<Category> findByParentIdIsNull();
    List<Category> findByParentId(String parentId);
    Optional<Category> findByName(String name);
    boolean existsByName(String name);
}
