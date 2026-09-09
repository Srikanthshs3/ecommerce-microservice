package org.praskanth.service.interfc;


import org.praskanth.dto.ProductRequest;
import org.praskanth.dto.ProductResponse;

import java.io.IOException;
import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request, String role)throws Exception;

    ProductResponse updateProduct(String id, ProductRequest request)throws IOException;

    void deleteProduct(String productId);

    ProductResponse getProductById(String id);

    List<ProductResponse> getAllProduct();

    //Response getProductsByCategory(Long categoryId);

    List<ProductResponse> getByCategory(String categoryId);

    List<ProductResponse> search(String name);
}
