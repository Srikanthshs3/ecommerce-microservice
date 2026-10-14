package org.praskanth.service.interfc;

import org.praskanth.dto.ProductRequest;
import org.praskanth.dto.ProductResponse;
import org.praskanth.security.gateway.GatewayUser;

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request, String role, List<String> categoryPath);

    /** A seller may change only their own products; an admin any. */
    ProductResponse updateProduct(String id, ProductRequest request, List<String> categoryPath, GatewayUser actor);

    /** A seller may delete only their own products; an admin any. */
    void deleteProduct(String productId, GatewayUser actor);

    ProductResponse getProductById(String id);

    /** categoryId matches the category AND everything below it. Both filters optional; newest first; capped by limit. */
    List<ProductResponse> find(String categoryId, String name, int limit);

    List<ProductResponse> findBySeller(Long sellerId);

    long countByCategory(String categoryId);
}
