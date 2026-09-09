package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank
    private String name;

    private String description;

    /** Null for a top-level category. Must reference an existing category id if set. */
    private String parentId;
}
