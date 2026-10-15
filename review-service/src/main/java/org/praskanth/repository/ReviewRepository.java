package org.praskanth.repository;

import org.praskanth.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductId(String productId, Pageable pageable);

    Page<Review> findByProductIdAndRating(String productId, int rating, Pageable pageable);

    Optional<Review> findByProductIdAndUserId(String productId, Long userId);

    boolean existsByProductIdAndUserId(String productId, Long userId);

    /** rows of [rating, count] */
    @Query("select r.rating, count(r) from Review r where r.productId = :productId group by r.rating")
    List<Object[]> countByRating(@Param("productId") String productId);

    /** rows of [productId, average, count] - one query for a whole page of product cards */
    @Query("select r.productId, avg(r.rating), count(r) from Review r where r.productId in :productIds group by r.productId")
    List<Object[]> summarize(@Param("productIds") Collection<String> productIds);
}
