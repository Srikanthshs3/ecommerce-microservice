package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One customer's review of one product. A customer can review a product only once (unique constraint), so a second
 * attempt is an edit. productId is a MongoDB ObjectId owned by product-service, hence a plain string (no FK).
 */
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_product_user", columnNames = {"product_id", "user_id"}),
        indexes = {
                @Index(name = "idx_review_product_created", columnList = "product_id, created_at"),
                @Index(name = "idx_review_product_rating", columnList = "product_id, rating")
        })
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Display name captured when the review was written (e.g. "Srikanth P."). Denormalised on purpose: no call to user-service per list view. */
    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(nullable = false)
    private int rating; // 1..5

    @Column(length = 120)
    private String title;

    @Column(length = 2000)
    private String comment;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<ReviewImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addImage(ReviewImage image) {
        image.setReview(this);
        image.setPosition(images.size());
        images.add(image);
    }
}
