package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The part of category-service's category that product-service needs to validate a product's category. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoryResponse {
    private String id;
    private String name;
    private Integer level;
    private boolean active;
    private boolean leaf;
    /** true = active, a leaf, and every ancestor active: products may be listed here */
    private Boolean selectable;
    private String parentId;
    /** root-first ids of all ancestors */
    private List<String> ancestorIds;
}
