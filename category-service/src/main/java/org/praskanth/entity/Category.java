package org.praskanth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One node of the 3-level category tree (department -> category -> brand).
 *
 * Storage pattern: "parent reference + denormalised ancestors + materialised path".
 *  - parentId            adjacency, for "children of X"
 *  - ancestors[]         root..parent with id/name/slug: a ready-made breadcrumb, no extra queries
 *  - ancestorIds[]       the same ids as plain strings (indexed): "all descendants of X" = ancestorIds contains X,
 *                        no recursion
 *  - slugPath            "mens/shirts/van-heusen": unique, SEO-friendly URL, lookup by path
 *  - childCount          maintained atomically ($inc): "is this a leaf?" without an extra query per row
 *
 * Products reference a category by id ONLY (never by name), so renaming a category never touches products.
 *
 * Stored in the "categories" collection. (The earlier flat model used "category"; that data is NOT migrated.)
 */
@Document(collection = "categories")
@CompoundIndexes({
        // names are unique among SIBLINGS only (two different parents may both have a "Levi's")
        @CompoundIndex(name = "uq_parent_name", def = "{'parentId': 1, 'nameKey': 1}", unique = true),
        @CompoundIndex(name = "ix_parent_order", def = "{'parentId': 1, 'displayOrder': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    private String name;

    /** lower-cased name, used only for the case-insensitive sibling-uniqueness index */
    private String nameKey;

    private String slug;

    @Indexed(unique = true)
    private String slugPath;

    private String description;

    /** an emoji or an image URL; purely presentational */
    private String icon;

    private String parentId;

    /** 1 = department, 2 = category, 3 = brand/type */
    private Integer level;

    /** root..parent with id/name/slug/level: a ready-made breadcrumb (display only) */
    @Builder.Default
    private List<CategoryAncestor> ancestors = new ArrayList<>();

    /** the same ancestors as plain ids: this is what "all descendants of X" queries use (indexed) */
    @Indexed
    @Builder.Default
    private List<String> ancestorIds = new ArrayList<>();

    @Builder.Default
    private Integer childCount = 0;

    @Builder.Default
    private Integer displayOrder = 0;

    /** inactive categories (and everything below them) disappear from the public tree */
    @Builder.Default
    private boolean active = true;

    /** user id of the creator (0 = system seed); decides who may edit/delete besides admins */
    @Indexed
    private Long createdBy;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public boolean isLeaf() {
        return childCount == null || childCount == 0;
    }
}