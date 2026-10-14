package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.praskanth.entity.CategoryAncestor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryResponse {
    private String id;
    private String name;
    private String slug;
    /** "mens/shirts/van-heusen" */
    private String slugPath;
    private String description;
    private String icon;
    private String parentId;
    /** 1, 2 or 3 */
    private Integer level;
    private Integer displayOrder;
    private boolean active;
    /** true when nothing is below it: only leaves can hold products */
    private boolean leaf;
    /** Only filled by GET /{id}: active, a leaf, and every ancestor active = products may be listed here. */
    private Boolean selectable;
    /** root..parent: ready-made breadcrumb */
    private List<CategoryAncestor> ancestors;
    /** the same ids as plain strings (root first): products store this as their category path */
    private List<String> ancestorIds;
    private Long createdBy;
    private Instant createdAt;
}