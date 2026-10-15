package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** One node of the nested tree returned by GET /api/v1/category/tree (active nodes only, in display order). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryNodeResponse {
    private String id;
    private String name;
    private String slug;
    private String slugPath;
    private String icon;
    private Integer level;
    private Integer displayOrder;
    private boolean leaf;
    @Builder.Default
    private List<CategoryNodeResponse> children = new ArrayList<>();
}