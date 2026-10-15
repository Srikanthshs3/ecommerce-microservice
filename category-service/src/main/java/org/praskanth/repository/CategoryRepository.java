package org.praskanth.repository;

import org.praskanth.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {

    /** everything visible in the storefront tree (the service drops nodes below an inactive parent) */
    List<Category> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    /** admin / seller management view: all nodes, inactive included */
    List<Category> findAllByOrderByLevelAscDisplayOrderAscNameAsc();

    List<Category> findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Category> findByParentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(String parentId);

    List<Category> findByLevelAndActiveTrueOrderByDisplayOrderAscNameAsc(Integer level);

    List<Category> findByCreatedByOrderByLevelAscNameAsc(Long createdBy);

    /** all descendants of X: X is somewhere in their ancestorIds[] (indexed) */
    List<Category> findByAncestorIds(String ancestorId);

    Optional<Category> findBySlugPath(String slugPath);

    boolean existsByParentIdAndNameKey(String parentId, String nameKey);

    long countByParentId(String parentId);
}
