package org.praskanth;

import org.praskanth.dto.ProductRequest;
import org.praskanth.dto.VariantDtos;
import org.praskanth.model.Product;
import org.praskanth.model.ProductVariant;
import org.praskanth.security.gateway.GatewayUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small shared builders for product-service tests. */
public final class TestData {

    private TestData() {
    }

    public static GatewayUser user(Long id, String... roles) {
        return new GatewayUser(id, "user" + id + "@example.com", Set.of(roles));
    }

    public static GatewayUser admin() {
        return user(1L, "ADMIN");
    }

    public static GatewayUser seller(Long id) {
        return user(id, "SELLER");
    }

    /** A valid request for a simple (no variants) product. */
    public static ProductRequest simpleRequest() {
        ProductRequest r = new ProductRequest();
        r.setName("  Blue Shirt  ");
        r.setDescription("A shirt");
        r.setCategoryId("cat3");
        r.setSellerId(7L);
        r.setKycStatus("ACTIVE");
        r.setMrpPrice(new BigDecimal("1000.00"));
        r.setSellingPrice(new BigDecimal("800.00"));
        r.setStock(10);
        r.setColor("Blue");
        return r;
    }

    public static Map<String, String> options(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    public static VariantDtos.VariantRequest variantRequest(String id, Map<String, String> options,
                                                            String mrp, String selling, Integer stock) {
        return new VariantDtos.VariantRequest(id, options, new BigDecimal(mrp), new BigDecimal(selling), stock);
    }

    /** A valid request for a product with Colour/Size variants. */
    public static ProductRequest variantProductRequest() {
        ProductRequest r = simpleRequest();
        r.setMrpPrice(null);
        r.setSellingPrice(null);
        r.setStock(null);
        r.setOptionNames(new ArrayList<>(List.of("Colour", "Size")));
        r.setVariants(new ArrayList<>(List.of(
                variantRequest(null, options("Colour", "Blue", "Size", "M"), "1000", "800", 5),
                variantRequest(null, options("Colour", "Blue", "Size", "L"), "1200", "900", 3))));
        return r;
    }

    public static Product simpleProduct(String id, Long sellerId) {
        return Product.builder()
                .id(id)
                .name("Shirt")
                .categoryId("cat3")
                .categoryPath(List.of("cat1", "cat2", "cat3"))
                .sellerId(sellerId)
                .mrpPrice(new BigDecimal("1000.00"))
                .sellingPrice(new BigDecimal("800.00"))
                .discountPrice(new BigDecimal("20.00"))
                .stock(10)
                .imageUrl(new ArrayList<>())
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    public static ProductVariant variant(String id, String colour, String size, String mrp, String selling, boolean active) {
        return ProductVariant.builder()
                .id(id)
                .options(options("Colour", colour, "Size", size))
                .mrpPrice(new BigDecimal(mrp))
                .sellingPrice(new BigDecimal(selling))
                .discountPrice(BigDecimal.ZERO)
                .active(active)
                .build();
    }

    public static Product variantProduct(String id, Long sellerId) {
        Product p = simpleProduct(id, sellerId);
        p.setOptionNames(new ArrayList<>(List.of("Colour", "Size")));
        p.setVariants(new ArrayList<>(List.of(
                variant("v1", "Blue", "M", "1000", "800", true),
                variant("v2", "Red", "M", "1100", "900", true))));
        return p;
    }
}
