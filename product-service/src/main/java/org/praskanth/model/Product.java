package org.praskanth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(value = "products")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {
    @Id
    private String id;
    @Indexed
    private String categoryId;

    @Indexed
    private Long sellerId;
    @Indexed
    private String name;
    private String description;
    private List<String> imageUrl;
    private BigDecimal mrpPrice;
    private BigDecimal sellingPrice;
    private BigDecimal discountPrice;
    private String color;

    private Integer stock;

    @CreatedDate
    private Instant createdAt;
}
