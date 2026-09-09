package org.praskanth.entity;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;
    @NotNull
    @Indexed(unique = true)
    private String categoryId;
    @NotNull
    @Indexed(unique = true)
    private String name;
    private String description;
    private String parentId;
    private Integer level;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
