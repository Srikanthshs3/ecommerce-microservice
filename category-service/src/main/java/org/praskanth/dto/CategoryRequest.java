package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Used for create and update. On update, parentId cannot change (moving categories is not supported). */
@Data
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 80, message = "Name must be at most 80 characters")
    private String name;

    @Size(max = 500)
    private String description;

    /** emoji or image URL */
    @Size(max = 300)
    private String icon;

    /** Null = a top-level department. Otherwise must be the id of an existing category (max depth applies). */
    private String parentId;

    /** Position among siblings (lower first). Default: after the last sibling. */
    @PositiveOrZero
    private Integer displayOrder;

    /** Default true. false hides the category and everything below it from the storefront. */
    private Boolean active;
}