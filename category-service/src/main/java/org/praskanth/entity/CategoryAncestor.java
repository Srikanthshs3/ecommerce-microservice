package org.praskanth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryAncestor {
    private String id;
    private String name;
    private String slug;
    private Integer level;
}
