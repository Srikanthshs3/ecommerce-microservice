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
    /** the most specific category (a level-3 brand/type) the seller picked */
    @Indexed
    private String categoryId;

    /**
     * root..categoryId as ids, e.g. [mensId, shirtsId, vanHeusenId]. Lets "everything under Men's" be ONE indexed
     * query (categoryPath = mensId) instead of a recursive lookup. Ids only, so renaming a category never touches products.
     */
    @Indexed
    private List<String> categoryPath;

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

    /**
     * Option names of a product with variants, e.g. ["Colour", "Size"] or ["Bed size"] (at most 2). Null / empty = a
     * simple product (no selector in the shop). For a variant product mrpPrice / sellingPrice / discountPrice above
     * hold the CHEAPEST active variant ("From Rs X" on cards).
     */
    private List<String> optionNames;

    private List<ProductVariant> variants;

    /** Pictures per value of the first option (see {@link OptionGallery}); {@code imageUrl} above = common pictures. */
    private List<OptionGallery> galleries;

    @CreatedDate
    private Instant createdAt;
}
